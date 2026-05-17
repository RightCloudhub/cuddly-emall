package com.example.mall.integration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mall.application.catalog.CatalogCommands.CreateProductCommand;
import com.example.mall.application.catalog.CatalogCommands.VariantInput;
import com.example.mall.application.catalog.CatalogService;
import com.example.mall.application.inventory.InventoryService;
import com.example.mall.application.order.PlaceOrderCommand;
import com.example.mall.application.order.PlaceOrderResult;
import com.example.mall.application.order.PlaceOrderService;
import com.example.mall.application.user.JwtService;
import com.example.mall.application.user.UserRegistrationService;
import com.example.mall.domain.catalog.Product;
import com.example.mall.domain.catalog.ProductVariant;
import com.example.mall.domain.user.Address;
import com.example.mall.domain.user.AddressRepository;
import com.example.mall.domain.user.User;
import com.example.mall.domain.user.UserIdMapping;
import com.example.mall.domain.user.UserIdMappingRepository;
import com.example.mall.integration.askflow.AskFlowApiClient;
import com.example.mall.support.PostgresBackedTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Covers I1 (order lookup), I3 (ticket callback), I4 (auth bridge) and I6 (loyalty) end-to-end
 * through the full Spring Security + integration chain. The AskFlow outbound client is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "mall.askflow.kb-fixed-delay-ms=600000",
            "mall.askflow.user-fixed-delay-ms=600000",
            "mall.askflow.service-token=test-service-token",
            "mall.payment.mock.settlement-delay-ms=600000"
        })
class IntegrationEndpointsIntegrationTest extends PostgresBackedTest {

    private static final String SERVICE_BEARER = "Bearer test-service-token";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlaceOrderService placeOrderService;
    @Autowired CatalogService catalogService;
    @Autowired InventoryService inventoryService;
    @Autowired UserRegistrationService userRegistrationService;
    @Autowired AddressRepository addressRepository;
    @Autowired UserIdMappingRepository mappingRepository;
    @Autowired JwtService jwtService;
    @MockBean AskFlowApiClient askFlowApiClient;

    @Test
    void orderLookupRequiresServiceToken() throws Exception {
        mvc.perform(get("/api/v1/integration/orders/lookup").param("order_id", "MO000000000001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderLookupReturns200WithShape() throws Exception {
        User user =
                userRegistrationService.register(
                        "buyer-il-" + uniq(), "buyer-il-" + uniq() + "@example.com", "password123");
        Address address =
                addressRepository.save(
                        new Address(user.getId(), "A", "13800001111", "S", "S", "P", "addr", true));
        ProductVariant v = newPublishedSku("SPU-IL-1", "SKU-IL-1");

        PlaceOrderResult result =
                placeOrderService.place(
                        new PlaceOrderCommand(
                                user.getId(),
                                address.getId(),
                                null,
                                List.of(new PlaceOrderCommand.LineItem(v.getId(), 2))));
        String orderNo = result.order().getOrderNo();

        MvcResult res =
                mvc.perform(
                                get("/api/v1/integration/orders/lookup")
                                        .param("order_id", orderNo)
                                        .header("Authorization", SERVICE_BEARER))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode body = json.readTree(res.getResponse().getContentAsByteArray());
        assertThat(body.get("order_id").asText()).isEqualTo(orderNo);
        assertThat(body.get("status").asText()).isEqualTo("pending_payment");
        assertThat(body.get("currency").asText()).isEqualTo("CNY");
        assertThat(body.get("items")).hasSize(1);
        assertThat(body.get("items").get(0).get("sku").asText()).isEqualTo("SKU-IL-1");
    }

    @Test
    void orderLookupMissing404() throws Exception {
        mvc.perform(
                        get("/api/v1/integration/orders/lookup")
                                .param("order_id", "MO999999999999")
                                .header("Authorization", SERVICE_BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderLookupRejectsMalformedOrderId() throws Exception {
        mvc.perform(
                        get("/api/v1/integration/orders/lookup")
                                .param("order_id", "not-an-order")
                                .header("Authorization", SERVICE_BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketCallbackAcceptsAndIsIdempotent() throws Exception {
        User user =
                userRegistrationService.register(
                        "tcb-" + uniq(), "tcb-" + uniq() + "@example.com", "password123");
        UUID askflowUserId = UUID.randomUUID();
        mappingRepository.save(new UserIdMapping(user.getId(), askflowUserId));

        String body =
                "{"
                        + "\"ticket_id\":\"T-" + uniq() + "\","
                        + "\"status\":\"resolved\","
                        + "\"askflow_user_id\":\"" + askflowUserId + "\","
                        + "\"type\":\"refund\","
                        + "\"title\":\"我要退货 MO000000000001\""
                        + "}";

        MvcResult first =
                mvc.perform(
                                post("/api/v1/integration/tickets/callback")
                                        .header("Authorization", SERVICE_BEARER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json.readTree(first.getResponse().getContentAsByteArray()).get("status").asText())
                .isEqualTo("accepted");

        MvcResult second =
                mvc.perform(
                                post("/api/v1/integration/tickets/callback")
                                        .header("Authorization", SERVICE_BEARER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(json.readTree(second.getResponse().getContentAsByteArray()).get("status").asText())
                .isEqualTo("duplicate");
    }

    @Test
    void authBridgeIssuesAskflowTokenForMappedUser() throws Exception {
        User user =
                userRegistrationService.register(
                        "bridge-" + uniq(), "bridge-" + uniq() + "@example.com", "password123");
        UUID askflowUserId = UUID.randomUUID();
        mappingRepository.save(new UserIdMapping(user.getId(), askflowUserId));

        String mallJwt = jwtService.issueAccessToken(user);

        MvcResult res =
                mvc.perform(
                                post("/api/v1/integration/auth/bridge")
                                        .header("Authorization", "Bearer " + mallJwt))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode body = json.readTree(res.getResponse().getContentAsByteArray());
        assertThat(body.get("askflow_token").asText()).isNotBlank();
        assertThat(body.get("askflow_user_id").asText()).isEqualTo(askflowUserId.toString());
    }

    @Test
    void authBridgeFailsWhenMappingMissing() throws Exception {
        User user =
                userRegistrationService.register(
                        "nomap-" + uniq(), "nomap-" + uniq() + "@example.com", "password123");
        String mallJwt = jwtService.issueAccessToken(user);

        mvc.perform(
                        post("/api/v1/integration/auth/bridge")
                                .header("Authorization", "Bearer " + mallJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void loyaltyEndpointReturnsDefaultRow() throws Exception {
        User user =
                userRegistrationService.register(
                        "ly-" + uniq(), "ly-" + uniq() + "@example.com", "password123");
        UUID askflowUserId = UUID.randomUUID();
        mappingRepository.save(new UserIdMapping(user.getId(), askflowUserId));

        MvcResult res =
                mvc.perform(
                                get("/api/v1/integration/loyalty/points")
                                        .param("askflow_user_id", askflowUserId.toString())
                                        .header("Authorization", SERVICE_BEARER))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode body = json.readTree(res.getResponse().getContentAsByteArray());
        assertThat(body.get("user_id").asText()).isEqualTo(user.getId().toString());
        assertThat(body.get("points").asInt()).isZero();
        assertThat(body.get("tier").asText()).isEqualTo("BRONZE");
    }

    @Test
    void loyalty404WhenUnknownAskflowUser() throws Exception {
        mvc.perform(
                        get("/api/v1/integration/loyalty/points")
                                .param("askflow_user_id", UUID.randomUUID().toString())
                                .header("Authorization", SERVICE_BEARER))
                .andExpect(status().isNotFound());
    }

    private ProductVariant newPublishedSku(String spu, String sku) {
        Product product =
                catalogService.createDraft(
                        new CreateProductCommand(
                                spu,
                                "Test " + spu,
                                "desc",
                                null,
                                "policy",
                                List.of(new VariantInput(sku, Map.of(), new BigDecimal("12.50"), 100))));
        ProductVariant v = catalogService.variantsOf(product.getId()).get(0);
        inventoryService.restock(v.getId(), 10);
        return v;
    }

    private static String uniq() {
        return String.valueOf(System.nanoTime());
    }
}
