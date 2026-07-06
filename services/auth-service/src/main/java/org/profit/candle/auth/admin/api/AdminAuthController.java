package org.profit.candle.auth.admin.api;

import lombok.RequiredArgsConstructor;
import org.profit.candle.auth.admin.api.dto.AdminLoginRequest;
import org.profit.candle.auth.admin.service.AdminLoginService;
import org.profit.candle.auth.api.AuthTokenResponder;
import org.profit.candle.auth.api.dto.OAuthLoginResponse;
import org.profit.candle.auth.exception.AuthErrorCode;
import org.profit.candle.auth.exception.AuthException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminLoginService adminLoginService;
    private final AuthTokenResponder tokenResponder;

    @PostMapping("/login")
    public ResponseEntity<OAuthLoginResponse> login(@RequestBody AdminLoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new AuthException(AuthErrorCode.INVALID_ADMIN_REQUEST);
        }
        return tokenResponder.tokenResponse(
                adminLoginService.login(request.username(), request.password()).tokens(), false);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
