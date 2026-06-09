dependencies {
    // Core Android/Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    
    // ViewModel / Lifecycle Compose Integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    
    // PDF Engine
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    
    // Networking/Data
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
