package com.playground.distributed;

import static org.assertj.core.api.Assertions.assertThat;

import com.playground.distributed.case1.Case1Controller.InventoryInitRequest;
import com.playground.distributed.case1.Case1Controller.PurchaseRequest;
import com.playground.distributed.case1.PurchaseResult;
import com.playground.distributed.case1.RateLimitResult;
import com.playground.distributed.case2.Case2Controller.JobSubmitRequest;
import com.playground.distributed.case3.Case3Controller.BroadcastRequest;
import com.playground.distributed.case4.Case4Controller.FaultRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NullObjectPatternTest {

    @Test
    @DisplayName("PurchaseRequest Null Object pattern normalizes null or invalid inputs")
    void testPurchaseRequestNullObject() {
        PurchaseRequest defaultReq = PurchaseRequest.ofNullable(null);
        assertThat(defaultReq.itemId()).isEqualTo("item-1");
        assertThat(defaultReq.quantity()).isEqualTo(1);

        PurchaseRequest normalizedReq = new PurchaseRequest("   ", -5);
        assertThat(normalizedReq.itemId()).isEqualTo("item-1");
        assertThat(normalizedReq.quantity()).isEqualTo(1);

        PurchaseRequest validReq = new PurchaseRequest("custom-item", 3);
        assertThat(validReq.itemId()).isEqualTo("custom-item");
        assertThat(validReq.quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("InventoryInitRequest Null Object pattern normalizes null or invalid inputs")
    void testInventoryInitRequestNullObject() {
        InventoryInitRequest defaultReq = InventoryInitRequest.ofNullable(null);
        assertThat(defaultReq.itemId()).isEqualTo("item-1");
        assertThat(defaultReq.stock()).isEqualTo(10);

        InventoryInitRequest normalizedReq = new InventoryInitRequest(null, null);
        assertThat(normalizedReq.itemId()).isEqualTo("item-1");
        assertThat(normalizedReq.stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("JobSubmitRequest Null Object pattern normalizes null or invalid inputs")
    void testJobSubmitRequestNullObject() {
        JobSubmitRequest defaultReq = JobSubmitRequest.ofNullable(null);
        assertThat(defaultReq.taskType()).isEqualTo("heavy_computation");
        assertThat(defaultReq.payload()).containsEntry("duration_sec", 1);

        JobSubmitRequest normalizedReq = new JobSubmitRequest("", null);
        assertThat(normalizedReq.taskType()).isEqualTo("heavy_computation");
        assertThat(normalizedReq.payload()).containsEntry("duration_sec", 1);
    }

    @Test
    @DisplayName("BroadcastRequest Null Object pattern normalizes null or invalid inputs")
    void testBroadcastRequestNullObject() {
        BroadcastRequest defaultReq = BroadcastRequest.ofNullable(null);
        assertThat(defaultReq.roomId()).isEqualTo("general");
        assertThat(defaultReq.sender()).isEqualTo("anonymous");
        assertThat(defaultReq.content()).isEmpty();

        BroadcastRequest normalizedReq = new BroadcastRequest(" ", " ", null);
        assertThat(normalizedReq.roomId()).isEqualTo("general");
        assertThat(normalizedReq.sender()).isEqualTo("anonymous");
        assertThat(normalizedReq.content()).isEmpty();
    }

    @Test
    @DisplayName("FaultRequest Null Object pattern normalizes null inputs to true")
    void testFaultRequestNullObject() {
        FaultRequest defaultReq = FaultRequest.ofNullable(null);
        assertThat(defaultReq.isHealthy()).isTrue();

        FaultRequest explicitFalse = new FaultRequest(false);
        assertThat(explicitFalse.isHealthy()).isFalse();

        FaultRequest nullFieldReq = new FaultRequest(null);
        assertThat(nullFieldReq.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("PurchaseResult Sentinel Objects have correct state")
    void testPurchaseResultSentinels() {
        assertThat(PurchaseResult.OUT_OF_STOCK.success()).isFalse();
        assertThat(PurchaseResult.OUT_OF_STOCK.remainingStock()).isZero();
        assertThat(PurchaseResult.OUT_OF_STOCK.message()).isEqualTo("Out of stock");

        assertThat(PurchaseResult.TIMEOUT.success()).isFalse();
        assertThat(PurchaseResult.TIMEOUT.remainingStock()).isZero();
        assertThat(PurchaseResult.TIMEOUT.message())
                .isEqualTo("Server busy, please retry in a moment");

        PurchaseResult ok = PurchaseResult.ok(10);
        assertThat(ok.success()).isTrue();
        assertThat(ok.remainingStock()).isEqualTo(10);
        assertThat(ok.message()).isEqualTo("Purchase successful");
    }

    @Test
    @DisplayName("RateLimitResult Sentinel Object has correct state")
    void testRateLimitResultSentinels() {
        assertThat(RateLimitResult.BLOCKED.allowed()).isFalse();
        assertThat(RateLimitResult.BLOCKED.remaining()).isZero();

        RateLimitResult allowed = RateLimitResult.of(true, 5);
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.remaining()).isEqualTo(5);
    }
}
