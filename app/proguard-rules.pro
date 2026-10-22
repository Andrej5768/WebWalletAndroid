# Keep the generated API models readable in crash reports; kotlinx.serialization keeps what it needs itself
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit: generic signatures of the API interfaces are read at runtime
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
