package dev.portafolio.explorer.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.portafolio.explorer.api.ExplorerDtos.ClusterSummary;
import dev.portafolio.explorer.api.ExplorerDtos.IndexMapping;
import dev.portafolio.explorer.api.ExplorerDtos.IndexSummary;
import dev.portafolio.explorer.api.ExplorerDtos.SearchResult;

@RestController
@RequestMapping("/api")
public class ExplorerController {

    private final ExplorerService service;

    public ExplorerController(ExplorerService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Principal user) {
        return Map.of("username", user.getName(), "clusters", service.clusters(user.getName()));
    }

    @GetMapping("/clusters")
    public List<ClusterSummary> clusters(Principal user) {
        return service.clusters(user.getName());
    }

    @GetMapping("/clusters/{cluster}/indices")
    public List<IndexSummary> indices(Principal user, @PathVariable String cluster) {
        return service.indices(user.getName(), cluster);
    }

    @GetMapping("/clusters/{cluster}/indices/{index}/mapping")
    public IndexMapping mapping(Principal user, @PathVariable String cluster, @PathVariable String index) {
        return service.mapping(user.getName(), cluster, index);
    }

    @GetMapping("/clusters/{cluster}/indices/{index}/search")
    public SearchResult search(Principal user, @PathVariable String cluster, @PathVariable String index,
                               @RequestParam String field, @RequestParam String value,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return service.search(user.getName(), cluster, index, field, value, page, size);
    }
}
