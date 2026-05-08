import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  ReactNode,
  useCallback,
} from "react";
import {
  UserManager,
  User,
  WebStorageStateStore,
  type UserManagerSettings,
} from "oidc-client-ts";

// ── Configuration ────────────────────────────────────────────────────────────
const keycloakConfig: UserManagerSettings = {
  authority: "http://localhost:1001/realms/iots-client",
  client_id: "iots-client",
  redirect_uri: window.location.origin + "/",
  post_logout_redirect_uri: window.location.origin + "/login",
  response_type: "code",
  scope: "openid profile email offline_access", // Added offline_access for refresh tokens
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  automaticSilentRenew: true, // Enable automatic token renewal
  validateIdTokenByIssuer: true,
  includeIdTokenInSilentRenew: true,
  staleStateAgeInSeconds: 300,
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

  const handleLogout = useCallback(async () => {
    try {
      // 1. Clear local user state in userManager
      await userManager.removeUser();
      // 2. Update local react state
      setUser(null);
      // 3. Clear session storage/local storage related to OIDC
      userManager.clearStaleState();
      // 4. Redirect to login
      window.location.href = "/login";
    } catch (error) {
      console.error("Logout failed:", error);
      window.location.href = "/login";
    }
  }, []);

  useEffect(() => {
    // Initial user load
    userManager.getUser().then((u) => {
      if (u && !u.expired) {
        setUser(u);
      } else if (u && u.expired) {
        handleLogout();
      }
      setIsLoading(false);
    });

    // Events
    const onUserLoaded = (u: User) => {
      console.log("User loaded:", u.profile.preferred_username);
      setUser(u);
    };

    const onUserUnloaded = () => {
      console.log("User unloaded");
      setUser(null);
    };

    const onAccessTokenExpiring = () => {
      console.log("Token expiring soon...");
      // userManager.signinSilent() is usually triggered automatically by automaticSilentRenew: true
    };

    const onAccessTokenExpired = () => {
      console.log("Token expired. Logging out...");
      handleLogout();
    };

    const onSilentRenewError = (err: Error) => {
      console.error("Silent renew error:", err);
      handleLogout();
    };

    userManager.events.addUserLoaded(onUserLoaded);
    userManager.events.addUserUnloaded(onUserUnloaded);
    userManager.events.addAccessTokenExpiring(onAccessTokenExpiring);
    userManager.events.addAccessTokenExpired(onAccessTokenExpired);
    userManager.events.addSilentRenewError(onSilentRenewError);

    return () => {
      userManager.events.removeUserLoaded(onUserLoaded);
      userManager.events.removeUserUnloaded(onUserUnloaded);
      userManager.events.removeAccessTokenExpiring(onAccessTokenExpiring);
      userManager.events.removeAccessTokenExpired(onAccessTokenExpired);
      userManager.events.removeSilentRenewError(onSilentRenewError);
    };
  }, [handleLogout]);

  const login = async (username: string, password: string) => {
    const tokenUrl = `${keycloakConfig.authority}/protocol/openid-connect/token`;

    const params = new URLSearchParams();
    params.append("grant_type", "password");
    params.append("client_id", keycloakConfig.client_id!);
    params.append("username", username);
    params.append("password", password);
    params.append("scope", keycloakConfig.scope!);

    const response = await fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: params,
    });

    if (!response.ok) {
      const errorData = await response.json();
      throw new Error(errorData.error_description || "Login failed");
    }

    const tokenResponse = await response.json();

    const newUser = new User({
      id_token: tokenResponse.id_token,
      access_token: tokenResponse.access_token,
      refresh_token: tokenResponse.refresh_token,
      token_type: tokenResponse.token_type,
      scope: tokenResponse.scope,
      profile: {}, // In a real app, decode this from id_token
      expires_at: Math.floor(Date.now() / 1000) + tokenResponse.expires_in,
    });

    await userManager.storeUser(newUser);
    setUser(newUser);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user && !user.expired,
        login,
        logout: handleLogout,
      }}
    >
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
