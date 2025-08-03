# Complete Project Brief: Pixel Watch 2 Always-On Red/Green Display App

## Project Requirements
- **Device**: Pixel Watch 2 (Model: GBZ4S, made in 2023)
- **Functionality**: Full screen display that:
  - Shows solid red background with black text
  - Switches to solid green background with black text on tap
  - **Never** enters ambient mode, dims, or goes to black screen
  - Maintains full brightness and color saturation 24/7
- **Critical Constraint**: Must bypass all Wear OS power management

## Technical Implementation Path

### Why Native App (Not Watch Face)
Watch faces cannot achieve true always-on display. They're forced into ambient mode (dim/grayscale) by the OS. Only a native app with `FLAG_KEEP_SCREEN_ON` can prevent this.

### Core Code Implementation
```kotlin
package com.example.alwaysondisplay

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var rootLayout: LinearLayout
    private lateinit var textView: TextView
    private var isRed = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRITICAL: Prevent screen from ever dimming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Create layout programmatically
        rootLayout = LinearLayout(this).apply {
            setBackgroundColor(Color.RED)
            orientation = LinearLayout.ORIENTATION_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { toggleColors() }
        }
        
        // Add text
        textView = TextView(this).apply {
            text = "TAP TO CHANGE"
            setTextColor(Color.BLACK)
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(50, 100, 50, 0)
            }
        }
        
        rootLayout.addView(textView)
        setContentView(rootLayout)
    }
    
    private fun toggleColors() {
        if (isRed) {
            rootLayout.setBackgroundColor(Color.GREEN)
            isRed = false
        } else {
            rootLayout.setBackgroundColor(Color.RED)
            isRed = true
        }
    }
}
```

### Manifest Configuration
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.alwaysondisplay">
    
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    
    <uses-feature android:name="android.hardware.type.watch" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Always On Display"
        android:theme="@android:style/Theme.DeviceDefault">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <uses-library
            android:name="com.google.android.wearable"
            android:required="true" />
            
    </application>
</manifest>
```

### Build Configuration (build.gradle)
```gradle
android {
    compileSdk 33
    
    defaultConfig {
        applicationId "com.example.alwaysondisplay"
        minSdk 30  // Wear OS 3
        targetSdk 33
        versionCode 1
        versionName "1.0"
    }
}

dependencies {
    implementation 'androidx.wear:wear:1.3.0'
}
```

## Deployment Process

### Step 1: Enable Developer Mode on Watch
1. On watch: Settings → System → About → Versions
2. Tap "Build number" 7 times rapidly
3. Go back to System settings
4. Open "Developer options"
5. Enable "ADB debugging"
6. Enable "Debug over Wi-Fi"

### Step 2: Connect via ADB
**Option A - USB (Recommended for Pixel Watch 2):**
1. Place watch on charging cradle
2. Connect cradle to computer via USB
3. Run: `adb devices`
4. Approve connection on watch

**Option B - Wireless:**
1. Note IP address shown in Developer options
2. Run: `adb pair [IP]:[PORT] [PAIRING_CODE]`
3. Then: `adb connect [IP]:[PORT]`

### Step 3: Build and Install
```bash
# Build the APK
./gradlew assembleDebug

# Install to watch
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.example.alwaysondisplay/.MainActivity
```

## Critical Warnings & Limitations

### Hardware Damage Risk
- **OLED Burn-in**: Static red/green colors WILL cause permanent screen damage
- **Timeline**: Expect visible burn-in within 1-2 weeks of continuous use
- **Mitigation**: None possible with these requirements

### Battery Impact
- Normal usage: 24-30 hours
- With this app: 15-20 hours maximum
- Continuous charging may cause overheating

### Thermal Concerns
- Pixel Watch 2 may overheat with constant screen-on
- System may force shutdown if temperature exceeds limits
- More likely during charging + always-on combination

### System Behavior
- OS will fight this implementation
- May force return to watch face after extended periods
- Critical battery (<10%) may override FLAG_KEEP_SCREEN_ON

## Alternative Recommendations

### Production-Ready Approach
Use Ongoing Activity API for visibility without hardware damage:
```kotlin
// Allows system power management while keeping app accessible
val ongoingActivity = OngoingActivity.Builder(this, NOTIFICATION_ID, notificationBuilder)
    .setStaticIcon(R.drawable.icon)
    .setTouchIntent(pendingIntent)
    .build()
```

### Safer Color Choices
- Use darker colors (#800000 instead of #FF0000)
- Add subtle animations to prevent static burn-in
- Implement automatic color rotation every few minutes

## Testing Checklist
- [ ] App stays on for 30+ minutes without dimming
- [ ] Tap successfully toggles between red/green
- [ ] Screen remains on when watch is stationary
- [ ] App survives system UI interactions
- [ ] Battery drain rate measured
- [ ] Temperature monitoring during extended use

## Troubleshooting

**App goes to ambient mode anyway:**
- Verify FLAG_KEEP_SCREEN_ON is set
- Check if battery saver is enabled
- Ensure no power management apps installed

**Can't connect via ADB:**
- Try USB connection via charging cradle
- Disable/re-enable developer options
- Restart both devices
- Check firewall settings

**Installation fails:**
- Verify minSdk matches Wear OS version
- Check available storage on watch
- Ensure previous version fully uninstalled

## Final Notes
This implementation goes against Wear OS design principles and will likely cause hardware damage. The client should understand these risks before proceeding. Consider demonstrating the burn-in risk with a short test period before committing to extended use.