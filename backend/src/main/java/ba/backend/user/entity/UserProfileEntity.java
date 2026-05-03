package ba.backend.user.entity;

import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "iots_user_profile")
public class UserProfileEntity extends BaseEntity {

    @Column(name = "up_external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "up_username", nullable = false, unique = true)
    private String username;

    @Column(name = "up_email")
    private String email;

    @Column(name = "up_first_name")
    private String firstName;

    @Column(name = "up_last_name")
    private String lastName;

    @Column(name = "up_roles")
    private String roles;

    protected UserProfileEntity() {}

    public UserProfileEntity(String externalId, String username, String email, 
                              String firstName, String lastName, String roles) {
        this.externalId = externalId;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
    }

    public String getExternalId() { return externalId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRoles() { return roles; }

    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setRoles(String roles) { this.roles = roles; }
}
