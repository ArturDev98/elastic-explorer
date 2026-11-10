package dev.portafolio.explorer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.portafolio.explorer.elastic.ElasticGateway;

@SpringBootTest
@AutoConfigureMockMvc
class ExplorerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ElasticGateway elastic;

    @Test
    void apiAnswers401WithoutSession() throws Exception {
        mvc.perform(get("/api/clusters")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void userWithoutAccessGets403AndElasticIsNeverCalled() throws Exception {
        mvc.perform(get("/api/clusters/staging/indices").with(user("soporte")))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.mensaje").value("No tienes acceso al clúster staging"));
        verifyNoInteractions(elastic);
    }

    @Test
    void indicesAreListed() throws Exception {
        when(elastic.indices(eq("local"))).thenReturn(List.of(
                Map.of("index", "orders", "health", "yellow", "docs.count", "12", "store.size", "2048")));

        mvc.perform(get("/api/clusters/local/indices").with(user("soporte")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("orders"))
           .andExpect(jsonPath("$[0].documents").value(12));
    }

    @Test
    void badSearchesAre400() throws Exception {
        when(elastic.mappingProperties(eq("local"), any())).thenReturn(Map.of("orderId", Map.of("type", "long")));

        mvc.perform(get("/api/clusters/local/indices/orders/search")
                        .param("field", "orderId").param("value", "abc").with(user("admin")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.mensaje").value("El campo orderId es numérico y \"abc\" no es un número"));
    }
}
