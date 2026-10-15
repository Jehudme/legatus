package dev.legatus.server.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String CLUSTER_ADMIN = "ROLE_CLUSTER_ADMIN";

    public static final String OPERATOR = "ROLE_OPERATOR";

    public static final String AUDITOR = "ROLE_AUDITOR";

    public static final String USER = "ROLE_USER";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
