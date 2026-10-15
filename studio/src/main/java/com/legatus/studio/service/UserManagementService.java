package com.legatus.studio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.legatus.studio.protocol.RestDto;
import com.legatus.studio.transport.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing cluster operator accounts in the Admin Console.
 */
public class UserManagementService {

    private final RestClient restClient;

    public UserManagementService(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    public CompletableFuture<List<RestDto.AccountDto>> getAccounts() {
        return restClient.get("/api/v1/admin/accounts", new TypeReference<List<RestDto.AccountDto>>() {});
    }

    public CompletableFuture<Void> createAccount(String username, String email, String password, String role) {
        RestDto.CreateAccountRequest request = new RestDto.CreateAccountRequest(
                username,
                email,
                password,
                role != null ? role : "OPERATOR"
        );
        return restClient.post("/api/v1/admin/accounts", request, Void.class);
    }

    public CompletableFuture<Void> updateAccount(String accountId, String email, String role) {
        RestDto.UpdateAccountRequest request = new RestDto.UpdateAccountRequest(email, role);
        return restClient.put("/api/v1/admin/accounts/" + accountId, request, Void.class);
    }

    public CompletableFuture<Void> updateAccountStatus(String accountId, String status) {
        RestDto.UpdateAccountStatusRequest request = new RestDto.UpdateAccountStatusRequest(status);
        return restClient.patch("/api/v1/admin/accounts/" + accountId + "/status", request, Void.class);
    }

    public CompletableFuture<Void> deleteAccount(String accountId) {
        return restClient.delete("/api/v1/admin/accounts/" + accountId, Void.class);
    }

    public CompletableFuture<Void> resetPassword(String accountId, String newPassword) {
        RestDto.ResetPasswordRequest request = new RestDto.ResetPasswordRequest(newPassword);
        return restClient.post("/api/v1/admin/accounts/" + accountId + "/reset-password", request, Void.class);
    }
}
