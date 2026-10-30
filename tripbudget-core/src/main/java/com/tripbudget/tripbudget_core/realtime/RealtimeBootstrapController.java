package com.tripbudget.tripbudget_core.realtime;

import com.tripbudget.tripbudget_core.common.annotations.CurrentUser;
import com.tripbudget.tripbudget_core.common.dtos.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/realtime")
public class RealtimeBootstrapController {
    public record Bootstrap(String headerName, String token) {}

    @GetMapping("/bootstrap")
    public ResponseEntity<ApiResponse<Bootstrap>> bootstrap(@CurrentUser CurrentUserDto user, CsrfToken csrf) {
        Long currentUserId = user.id();
        var body = new Bootstrap(csrf.getHeaderName(), csrf.getToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(HttpStatus.OK, "Realtime ready", body));
    }
}
