package com.cinemahub.controller;

import com.cinemahub.service.FeedbackService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Function 5, staff-facing side: the Customer Relations Executive queue.
 * Restricted to CUSTOMER_RELATIONS_EXECUTIVE / SYSTEM_ADMIN via the
 * "/cre/**" rule in SecurityConfig.
 */
@Controller
@RequestMapping("/cre/feedback")
public class CustomerRelationsController {

    private final FeedbackService feedbackService;

    public CustomerRelationsController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("feedbackList", feedbackService.findAll());
        return "feedback/manage-list";
    }

    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id) {
        feedbackService.markResolved(id);
        return "redirect:/cre/feedback";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        feedbackService.delete(id);
        return "redirect:/cre/feedback";
    }
}
