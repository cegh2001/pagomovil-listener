# Add project specific ProGuard rules here.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-keep class com.cegh.pagomovillistener.data.remote.PaymentPayload { *; }
