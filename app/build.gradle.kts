dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Official Google GenAI SDK (Replaces OkHttp for API calls)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tomroush.pdfbox)
}
