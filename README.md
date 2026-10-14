# 📱 Android APK Builder - No Android Studio Required!

[![Build Status](https://github.com/wasishah33/android-apk-builder/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/wasishah33/android-apk-builder/actions/workflows/android-build.yml)
[![License](https://img.shields.io/github/license/wasishah33/android-apk-builder)](LICENSE)
[![Stars](https://img.shields.io/github/stars/wasishah33/android-apk-builder?style=social)](https://github.com/wasishah33/android-apk-builder/stargazers)
[![Forks](https://img.shields.io/github/forks/wasishah33/android-apk-builder?style=social)](https://github.com/wasishah33/android-apk-builder/network/members)
[![Open Issues](https://img.shields.io/github/issues/wasishah33/android-apk-builder)](https://github.com/wasishah33/android-apk-builder/issues)
[![Open PRs](https://img.shields.io/github/issues-pr/wasishah33/android-apk-builder)](https://github.com/wasishah33/android-apk-builder/pulls)
[![Last Commit](https://img.shields.io/github/last-commit/wasishah33/android-apk-builder)](https://github.com/wasishah33/android-apk-builder/commits)

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-8.0-02303A?logo=gradle&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2021%2B-3DDC84?logo=android&logoColor=white)
![CI/CD](https://img.shields.io/badge/GitHub%20Actions-Ready-2088FF?logo=github-actions&logoColor=white)

> **Build Android APKs using GitHub Actions without installing Android Studio locally**

A complete Android project template that demonstrates how to build APK files using GitHub Actions. Perfect for developers who want to create Android apps without setting up the full Android development environment locally.

## 🚀 Quick Start

1. **[Fork this repository](https://github.com/wasishah33/android-apk-builder/fork)**
2. **Clone to your computer**: `git clone https://github.com/wasishah33/android-apk-builder.git`
3. **Make your changes** (see [Customization Guide](#-customization-guide))
4. **Push to GitHub**: GitHub Actions automatically builds your APK
5. **Download your APK** from the Actions tab

## 📋 Prerequisites

### Required Software (Windows/Mac/Linux)
- **Git** - [Download here](https://git-scm.com/)
- **Text Editor** - VS Code, Notepad++, or any editor
- **Web Browser** - Chrome, Firefox, Edge, etc.
- **GitHub Account** - [Sign up free](https://github.com)

### ✅ What You DON'T Need
- ❌ Android Studio
- ❌ Android SDK
- ❌ Java/Kotlin installation
- ❌ Gradle installation
- ❌ Emulator setup

## 🎯 Features

### Sample App Includes:
- ✨ **Modern Material Design 3** - Beautiful, responsive UI
- 🔢 **Click Counter** - Interactive button with counter
- 🎉 **Toast Notifications** - Celebratory messages every 5 clicks
- 📱 **Responsive Layout** - Works on all screen sizes
- 🎨 **Custom Theming** - Easy to customize colors and styles

### Build System Features:
- 🤖 **Automated Builds** - GitHub Actions builds APKs automatically
- 📦 **Multiple APK Types** - Debug and Release versions
- ⚡ **Fast Builds** - Cached dependencies for speed
- 📊 **Build Reports** - Detailed logs and error reporting
- 🔄 **CI/CD Ready** - Professional development workflow

## 📖 Detailed Setup Guide

### Step 1: Get the Code

#### Option A: Fork This Repository (Recommended)
1. Click the **"Fork"** button at the top of this repository
2. This creates your own copy that you can modify
3. Clone your fork: `git clone https://github.com/wasishah33/android-apk-builder.git`

#### Option B: Download and Create New Repository
1. Click **"Code" → "Download ZIP"**
2. Extract the files to your computer
3. Create a new repository on GitHub
4. Upload the files to your new repository

### Step 2: Install Required Software

#### Windows Users:
```powershell
# Install Git
# Download from: https://git-scm.com/download/win
# Follow the installer prompts (default options are fine)

# Install VS Code (optional but recommended)
# Download from: https://code.visualstudio.com/download
```

#### Mac Users:
```bash
# Install Git (if not already installed)
brew install git

# Install VS Code (optional)
brew install --cask visual-studio-code
```

#### Linux Users:
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install git

# For other distributions, use your package manager
```

### Step 3: Set Up Your Repository

1. **Open terminal/command prompt** in your project folder
2. **Initialize Git** (if you downloaded ZIP):
   ```bash
   git init
   git add .
   git commit -m "Initial Android project setup"
   ```
3. **Connect to GitHub**:
   ```bash
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
   git branch -M main
   git push -u origin main
   ```

### Step 4: Verify GitHub Actions

1. Go to your GitHub repository
2. Click the **"Actions"** tab
3. You should see the workflow running automatically
4. Wait for it to complete (usually 3-5 minutes)

## 🔧 How to Download Your APK

### Method 1: From GitHub Actions (Recommended)
1. **Navigate** to your repository on GitHub
2. **Click** the "Actions" tab
3. **Select** the latest successful workflow run (green checkmark ✅)
4. **Scroll down** to the "Artifacts" section
5. **Download** your APK:
   - `app-debug.apk` - For testing (recommended)
   - `app-release-unsigned.apk` - For distribution (needs signing)

### Method 2: Direct Links
GitHub provides direct download links for artifacts, but they expire after 90 days.

## 🎨 Customization Guide

### Change App Name and Package
1. **App Name**: Edit `app/src/main/res/values/strings.xml`
   ```xml
   <string name="app_name">Your App Name</string>
   ```

2. **Package Name**: Edit `app/build.gradle`
   ```gradle
   android {
       namespace 'com.yourcompany.yourapp'
       defaultConfig {
           applicationId "com.yourcompany.yourapp"
       }
   }
   ```

3. **Update Folder Structure**: 
   - Rename `app/src/main/java/com/example/sampleapp/` 
   - To `app/src/main/java/com/yourcompany/yourapp/`

### Customize the User Interface

#### Change Colors
Edit `app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="primary_text">#FF1976D2</color>        <!-- Blue -->
    <color name="secondary_text">#FF666666</color>      <!-- Gray -->
    <color name="button_color">#FF4CAF50</color>        <!-- Green -->
    <color name="background_color">#FFF5F5F5</color>    <!-- Light Gray -->
</resources>
```

#### Change Text Content
Edit `app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">My Awesome App</string>
    <string name="welcome_message">Welcome to My App!</string>
    <string name="click_me">Tap Here!</string>
</resources>
```

#### Modify Layout
Edit `app/src/main/res/layout/activity_main.xml` to change the UI structure.

### Add App Functionality

#### Basic Button Click Handler
Edit `app/src/main/java/com/example/sampleapp/MainActivity.kt`:
```kotlin
clickButton.setOnClickListener {
    // Your custom logic here
    Toast.makeText(this, "Button clicked!", Toast.LENGTH_SHORT).show()
}
```

#### Add New Activities
1. Create new Kotlin file in the same package
2. Create corresponding layout XML file
3. Register in `AndroidManifest.xml`

### App Icon and Branding

#### Change App Icon
1. **Prepare icons** in these sizes:
   - 48x48 (mdpi)
   - 72x72 (hdpi)
   - 96x96 (xhdpi)
   - 144x144 (xxhdpi)
   - 192x192 (xxxhdpi)

2. **Replace files** in `app/src/main/res/mipmap-*/`
3. **Update** `AndroidManifest.xml` if needed

## 🏗️ Project Structure Explained

```
android-apk-builder/
├── 📁 .github/workflows/           # GitHub Actions Configuration
│   └── android-build.yml          # Main build workflow
├── 📁 app/                         # Android App Module
│   ├── build.gradle               # App-level build configuration
│   ├── proguard-rules.pro         # Code obfuscation rules
│   └── 📁 src/main/
│       ├── AndroidManifest.xml    # App permissions & components
│       ├── 📁 java/com/example/sampleapp/
│       │   └── MainActivity.kt    # Main app logic (Kotlin)
│       └── 📁 res/                # App resources
│           ├── 📁 layout/         # UI layouts (XML)
│           ├── 📁 values/         # Strings, colors, themes
│           ├── 📁 mipmap-*/       # App icons
│           └── 📁 xml/            # XML configurations
├── 📁 gradle/wrapper/             # Gradle wrapper files
├── build.gradle                   # Project-level build config
├── gradle.properties             # Gradle settings
├── settings.gradle                # Project modules
├── gradlew                        # Gradle wrapper (Unix)
├── gradlew.bat                    # Gradle wrapper (Windows)
├── .gitignore                     # Git ignore rules
└── README.md                      # This documentation
```

### Key Files Explained:

#### `.github/workflows/android-build.yml`
- **Purpose**: Defines the automated build process
- **Triggers**: Runs on push to main/master branch
- **Actions**: Sets up environment, builds APK, uploads artifacts

#### `app/build.gradle`
- **Purpose**: App-specific build configuration
- **Contains**: Dependencies, build types, SDK versions
- **Modify**: To add libraries or change build settings

#### `MainActivity.kt`
- **Purpose**: Main app logic and user interface handling
- **Language**: Kotlin (modern Android development language)
- **Modify**: To add app functionality

#### `app/src/main/res/`
- **Purpose**: Contains all app resources
- **Includes**: Layouts, strings, colors, images, icons
- **Modify**: To change app appearance and content

## 🚀 Advanced Usage

### Adding Dependencies

Add new libraries to `app/build.gradle`:
```gradle
dependencies {
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    // Add more dependencies here
}
```

### Build Variants

Create different app versions by modifying `app/build.gradle`:
```gradle
android {
    buildTypes {
        debug {
            applicationIdSuffix ".debug"
            versionNameSuffix "-DEBUG"
        }
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
        }
    }
}
```

### Environment Variables

Use GitHub secrets for sensitive data:
1. Go to repository **Settings → Secrets and variables → Actions**
2. Add your secrets (API keys, signing keys, etc.)
3. Reference in workflow: `${{ secrets.YOUR_SECRET_NAME }}`

## 🎯 Use Cases

### Perfect For:
- 🎓 **Learning Android Development** - No complex setup required
- 🚀 **Rapid Prototyping** - Quick app testing and iteration
- 👥 **Team Collaboration** - Consistent build environment
- 📚 **Educational Projects** - Focus on code, not configuration
- 🧪 **Proof of Concepts** - Fast validation of ideas
- 🔄 **CI/CD Pipeline** - Automated testing and deployment

### Examples:
- **Simple Utility Apps** - Calculators, converters, timers
- **Learning Projects** - Following Android tutorials
- **MVP Applications** - Testing app concepts quickly
- **Open Source Contributions** - Easy for contributors to build
- **Client Demos** - Quick APK generation for stakeholders

## 🛠️ Troubleshooting Guide

### Common Issues and Solutions

#### ❌ Build Fails - "Gradle wrapper not found"
**Problem**: Missing or corrupted Gradle wrapper files
**Solution**:
1. Ensure `gradlew` and `gradlew.bat` files are present
2. Check file permissions: `chmod +x gradlew` (Linux/Mac)
3. Re-download Gradle wrapper:
   ```bash
   gradle wrapper --gradle-version 8.0
   ```

#### ❌ Build Fails - "SDK not found"
**Problem**: Android SDK path issues in GitHub Actions
**Solution**: This is automatically handled by the workflow. If you see this error:
1. Check `.github/workflows/android-build.yml` is present
2. Verify the workflow uses `actions/setup-java@v4`
3. Ensure `android.useAndroidX=true` is in `gradle.properties`

#### ❌ APK Download Not Available
**Problem**: No artifacts in GitHub Actions
**Solutions**:
1. **Check Build Status**: Ensure the workflow completed successfully (green checkmark)
2. **Wait for Completion**: Builds take 3-5 minutes
3. **Check Logs**: Click on the failed step to see error details
4. **Artifact Expiry**: Artifacts expire after 90 days

#### ❌ App Crashes on Install
**Problem**: APK installation fails or app crashes
**Solutions**:
1. **Use Debug APK**: Always test with `app-debug.apk` first
2. **Check Device Compatibility**: Ensure Android 5.0+ (API 21+)
3. **Enable Unknown Sources**: Allow installation from unknown sources
4. **Check Logs**: Use `adb logcat` to see crash details

#### ❌ Package Name Conflicts
**Problem**: "App not installed" due to package conflicts
**Solutions**:
1. **Uninstall Existing**: Remove any existing app with same package name
2. **Change Package**: Modify `applicationId` in `app/build.gradle`
3. **Use Debug Suffix**: Debug builds automatically add `.debug` suffix

#### ❌ GitHub Actions Quota Exceeded
**Problem**: "No more runner minutes available"
**Solutions**:
1. **Check Usage**: Go to Settings → Billing → Usage
2. **Free Tier Limits**: 2000 minutes/month for public repositories
3. **Optimize Builds**: Use caching to reduce build time
4. **Private Repos**: Consider upgrading to paid plan

### Build Performance Tips

#### 🚀 Speed Up Builds
1. **Enable Gradle Caching**: Already configured in the workflow
2. **Reduce Dependencies**: Only include necessary libraries
3. **Use Build Cache**:
   ```gradle
   org.gradle.caching=true
   ```
4. **Parallel Builds**:
   ```gradle
   org.gradle.parallel=true
   ```

#### 📊 Monitor Build Times
- Check "Actions" tab for build duration
- Typical build time: 3-5 minutes
- First build takes longer (dependency download)

### Debugging Your App

#### 🔍 Enable Debug Logging
Add to `MainActivity.kt`:
```kotlin
import android.util.Log

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "App started successfully")
        // ... rest of your code
    }
}
```

#### 📱 Testing on Device
1. **Install Debug APK**: Use `app-debug.apk` for testing
2. **Enable Developer Options**: Settings → About Phone → Tap Build Number 7 times
3. **USB Debugging**: Settings → Developer Options → USB Debugging
4. **View Logs**: Connect to computer and run `adb logcat`

## 📚 Learning Resources

### Android Development
- 📖 **[Android Developer Guide](https://developer.android.com/guide)** - Official documentation
- 🎓 **[Android Basics Course](https://developer.android.com/courses/android-basics-kotlin/course)** - Free Google course
- 📺 **[Android Development YouTube](https://www.youtube.com/user/androiddevelopers)** - Official channel

### Kotlin Programming
- 📖 **[Kotlin Documentation](https://kotlinlang.org/docs/home.html)** - Official Kotlin docs
- 🎓 **[Kotlin Bootcamp](https://developer.android.com/courses/kotlin-bootcamp/overview)** - Free course
- 💻 **[Kotlin Playground](https://play.kotlinlang.org/)** - Online Kotlin editor

### GitHub Actions
- 📖 **[GitHub Actions Documentation](https://docs.github.com/en/actions)** - Official docs
- 🎓 **[GitHub Learning Lab](https://lab.github.com/)** - Interactive tutorials
- 🔧 **[Android CI/CD Guide](https://docs.github.com/en/actions/guides/building-and-testing-java-with-gradle)** - Gradle builds

## 🤝 Contributing

### How to Contribute
1. **Fork** this repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** your changes: `git commit -m 'Add amazing feature'`
4. **Push** to the branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Contribution Ideas
- 🆕 **New Sample Apps** - Different app templates
- 🔧 **Workflow Improvements** - Better CI/CD features
- 📚 **Documentation** - More tutorials and guides
- 🐛 **Bug Fixes** - Fix issues and improve stability
- 🎨 **UI Templates** - More design examples

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 💬 Support

### Getting Help
- 🐛 **Bug Reports**: [Create an Issue](https://github.com/wasishah33/android-apk-builder/issues)
- 💡 **Feature Requests**: [Create an Issue](https://github.com/wasishah33/android-apk-builder/issues)
- 💬 **Questions**: [Start a Discussion](https://github.com/wasishah33/android-apk-builder/discussions)
- 📧 **Direct Contact**: your.email@example.com

### Community
- 🌟 **Star this repository** if it helped you!
- 🔄 **Share** with other developers
- 🐦 **Follow** on Twitter: [@yourusername](https://twitter.com/wasishah33)

## 🚀 What's Next?

### Planned Features
- [ ] 🔐 **App Signing** - Automatic APK signing workflow
- [ ] 🧪 **Unit Testing** - Automated testing integration
- [ ] 📊 **Code Coverage** - Test coverage reports
- [ ] 🎨 **UI Testing** - Espresso test examples
- [ ] 📱 **Multi-Module** - Complex app structure template
- [ ] 🌍 **Internationalization** - Multi-language support
- [ ] 📦 **Google Play** - Deployment automation

### Version History
- **v1.0** - Initial release with basic Android app and GitHub Actions
- **v1.1** - Added comprehensive documentation and troubleshooting
- **v1.2** - (Planned) App signing and testing integration

---

## ⭐ Acknowledgments

- 🤖 **GitHub Actions** - For providing free CI/CD for open source
- 📱 **Android Team** - For excellent documentation and tools
- 🎯 **Gradle** - For powerful build automation
- 💻 **JetBrains** - For Kotlin and excellent IDEs
- 🌟 **Open Source Community** - For inspiration and contributions

---

<div align="center">

**Made with ❤️ by developers, for developers**

[⭐ Star this repository](https://github.com/wasishah33/android-apk-builder) • [🐛 Report Bug](https://github.com/wasishah33/android-apk-builder/issues) • [💡 Request Feature](https://github.com/wasishah33/android-apk-builder/issues)

</div>

## My Invest Dashboard Preview

This fork now contains a first-pass Android investment dashboard UI in `app/src/main/java/com/example/sampleapp/MainActivity.kt`.

- Portfolio, investment overview, illustrative S&P 500 line chart, and navigation buttons.
- **Preview only:** displayed amounts and rates are fictional sample figures.
- Authentication, backend balances, deposits/withdrawals, admin controls, and live market data are not implemented yet.

A push to `main` should start the Android Build workflow in the Actions tab. When the workflow completes successfully, download the `app-debug` artifact for installation testing. The release artifact is unsigned and is not suitable for public distribution without signing.

