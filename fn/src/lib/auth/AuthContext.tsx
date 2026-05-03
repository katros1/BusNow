import React, { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { UserManager, User, WebStorageStateStore } from "oidc-client-ts";

// ── Configuration ────────────────────────────────────────────────────────────
const keycloakConfig = {
  authority: "http://localhost:1001/realms/iots-realm",
  client_id: "iots-client",
  redirect_uri: window.location.origin + "/",
  post_logout_redirect_uri: window.location.origin + "/login",
  response_type: "code",
  scope: "openid profile email",
  userStore: new WebStorageStateStore({ store: window.localStorage }),
};

const userManager = new UserManager(keycloakConfig);

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    userManager.getUser().then((u) => {
      setUser(u);
      setIsLoading(false);
    });

    const onUserLoaded = (u: User) => setUser(u);
    const onUserUnloaded = () => setUser(null);

    userManager.events.addUserLoaded(onUserLoaded);
    userManager.events.addUserUnloaded(onUserUnloaded);

    return () => {
      userManager.events.removeUserLoaded(onUserLoaded);
      userManager.events.removeUserUnloaded(onUserUnloaded);
    };
  }, []);

  const login = async (username: string, password: string) => {
    // Manual Direct Access Grant (Password Flow)
    // NOTE: Direct Access Grant must be enabled in Keycloak client settings
    const tokenUrl = `${keycloakConfig.authority}/protocol/openid-connect/token`;
    
    const params = new URLSearchParams();
    params.append("grant_type", "password");
    params.append("client_id", keycloakConfig.client_id);
    params.append("username", username);
    params.append("password", password);
    params.append("scope", keycloakConfig.scope);

    const response = await fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: params,
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error_description || "Login failed");
    }

    const tokenResponse = await response.json();
    
    // Create a User object from the response and store it
    // We use userManager to keep things in sync
    const newUser = new User({
      id_token: tokenResponse.id_token,
      access_token: tokenResponse.access_token,
      refresh_token: tokenResponse.refresh_token,
      token_type: tokenResponse.token_type,
      scope: tokenResponse.scope,
      profile: {}, // Usually decoded from id_token, but simplified here
      expires_at: Math.floor(Date.now() / 1000) + tokenResponse.expires_in,
    });

    await userManager.storeUser(newUser);
    setUser(newUser);
  };

  const logout = async () => {
    await userManager.removeUser();
    setUser(null);
    window.location.href = "/login";
  };

  return (
    <AuthContext.Provider value={{ 
      user, 
      isLoading, 
      isAuthenticated: !!user && !user.expired,
      login, 
      logout 
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
