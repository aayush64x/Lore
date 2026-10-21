package com.lore.ai.dto;

import java.util.List;

/**
 * A teach-back grade. verdict is "pass", "partly" or "fail". missing holds hints that point
 * at what's missing without giving the answer. model is the model that graded it.
 */
public record GradeResponse(String verdict, List<String> gotRight, List<String> missing, String model) {}
