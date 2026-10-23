# G.I Launcher

Cyber gold home screen + lock screen. Redmi 9A (`armeabi-v7a`, 32-bit, Android 10) အတွက်။ Native `.so` မပါ။

အလယ်မှာ **G.I**၊ အောက်မှာ **powered by I ko ko**။

## GitHub Actions

`main` ကို push တိုင်း APK တည်ဆောက်ပါတယ်။ လက်နဲ့လည်း run လို့ရပါတယ်။

1. Repo → **Actions** → **Build APK**
2. အောင်သွားရင် **Artifacts** ထဲက `GILauncher-redmi9a-armeabi-v7a` ကို ဒေါင်းလုဒ်လုပ်ပါ
3. Redmi 9A မှာ ဖွင့်ပြီး Install လုပ်ပါ (Unknown sources ခွင့်ပြုပါ)
4. Home ခလုတ် → **G.I Launcher** → **Always**
5. Security → Autostart ဖွင့်ပြီး Battery saver ကို **No restrictions** ထားပါ

ဖုန်းပိတ်ပြီး ပြန်ဖွင့်တိုင်း ရွှေရောင် lock screen ပေါ်ပါတယ်။ အပေါ်ဆွဲရင် home ထဲဝင်ပါတယ်။ System PIN/pattern ရှိရင် Android က အရင်မေးပါမယ်။

## လက်ဖြင့် build

လိုအပ်ချက်: JDK 17, Python 3 + Pillow, Android SDK platform 29 နဲ့ build-tools.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
pip install pillow
bash build.sh
```

APK က `dist/GILauncher.apk` မှာ ထွက်ပါတယ်။

လက်မှတ်က `gi.keystore` (alias `gi`, password `gilauncher`)။ update ထပ်ထည့်ရင် keystore အဟောင်းကိုပဲ သုံးပါ။
