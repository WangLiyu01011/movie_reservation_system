package me.wly.movie_reservation.ai;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.ai.dto.AiRequestDTO;
import me.wly.movie_reservation.ai.vo.AiChatReplyVO;
import me.wly.movie_reservation.ai.vo.AiSessionVO;
import me.wly.movie_reservation.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiApplicationService aiApplicationService;
    @PostMapping
    public ResponseEntity<ApiResponse<AiChatReplyVO>> processAiRequest(@Valid @RequestBody AiRequestDTO request, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(aiApplicationService.chat(request, owner(principal))));
    }
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<AiSessionVO>> createSession(Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(aiApplicationService.createSession(owner(principal))));
    }
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AiSessionVO> history(@PathVariable String sessionId, Principal principal) {
        return ApiResponse.success(aiApplicationService.getSession(sessionId, owner(principal)));
    }
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId, Principal principal) {
        aiApplicationService.deleteSession(sessionId, owner(principal));
        return ResponseEntity.noContent().build();
    }
    private String owner(Principal principal) { return principal == null ? null : principal.getName(); }
}
