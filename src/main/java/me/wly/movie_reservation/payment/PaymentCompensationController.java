package me.wly.movie_reservation.payment;

import lombok.RequiredArgsConstructor;
import me.wly.movie_reservation.common.api.ApiResponse;
import me.wly.movie_reservation.payment.vo.PaymentTaskVO;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Restricted to SYSTEM_ADMIN in SecurityConfig. */
@RestController @RequestMapping("/api/v1/payment-compensations") @RequiredArgsConstructor
public class PaymentCompensationController {
    private final PaymentTaskService tasks;
    @GetMapping
    public ApiResponse<List<PaymentTaskVO>> manualTasks() {
        return ApiResponse.success(tasks.manualTasks());
    }
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable Long id) {
        tasks.retryManual(id); return ApiResponse.success(null);
    }
}
