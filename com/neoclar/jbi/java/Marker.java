package com.neoclar.jbi.java;

public final class Marker {
    public static void beforeMarker() {}
    @Deprecated
    public static void afterMarker() {}
    public static void lineMarker(Integer line) {}
    public static void stopMarker() {}
    public static void deleteMarker(int line) {}
    public static void deleteMarker(int lineStart, int lineEnd) {}
    public static void variableMarker(Integer varID) {}
    public static void beforeOneMarker() {}
    public static void afterOneMarker() {}
    public static void lineOneMarker(Integer line) {}
    public static void GoToMarker(Integer line) {}
    public static void deleteMethodMarker() {}
    public static void anonymusClassMarker(Integer classID, Object classObject) {}
    public static void plug() {}
}