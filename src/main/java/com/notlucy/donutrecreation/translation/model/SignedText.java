/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
