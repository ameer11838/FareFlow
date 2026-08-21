package com.fareflow.assistant;

import com.fareflow.assistant.dto.AskRequest;
import com.fareflow.assistant.dto.AssistantConfigResponse;
import com.fareflow.assistant.dto.AssistantResponse;
import com.fareflow.auth.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ask FareFlow, always scoped to the authenticated rider. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final CurrentUserService currentUserService;

    public AssistantController(AssistantService assistantService,
                               CurrentUserService currentUserService) {
        this.assistantService = assistantService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/config")
    public AssistantConfigResponse config() {
        return assistantService.config(currentUserService.require());
    }

    @PostMapping("/ask")
    public AssistantResponse ask(@Valid @RequestBody AskRequest request) {
        return assistantService.ask(currentUserService.require(), request);
    }
}
