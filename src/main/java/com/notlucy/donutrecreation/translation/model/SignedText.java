package com.notlucy.donutrecreation.translation.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SignedText {
  private final String[] lines;
  private final Map<String, String[]> translations = new ConcurrentHashMap<>();

  public SignedText(String[] lines) {
    this.lines = lines.clone();
  }

  public String[] originalLines() {
    return lines.clone();
  }

  public void putTranslation(String lang, String[] text) {
    translations.put(lang, text.clone());
  }

  public String[] getTranslation(String lang) {
    String[] t = translations.get(lang);
    return t != null ? t.clone() : lines.clone();
  }
}