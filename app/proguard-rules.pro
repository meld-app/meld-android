# Preserve source filenames and line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable

# Retain zero-arg constructors on ViewModels instantiated reflectively by ViewModelProvider
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>();
}

# Preserve MeldToolId enum constant names persisted in SharedPreferences (home layout & recent results)
-keep enum app.meld.android.model.MeldToolId {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

