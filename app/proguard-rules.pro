# Preserve source filenames and line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable

# Retain zero-arg constructors on ViewModels instantiated reflectively by ViewModelProvider
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>();
}

