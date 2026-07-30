# Automatic Release

Release apne aap ban jaati hai — APK build hota hai, sign hota hai, aur GitHub Release me attach ho jaata hai.

## Release banane ke 2 tareeke

### Tareeka 1 — GitHub website se (bina terminal)

1. Repo kholo -> **Actions** tab
2. Left side me **Release** workflow choose karo
3. **Run workflow** dabao
4. Version daalo, jaise `v1.0.1`
5. **Run workflow** confirm karo

~5 minute me release ready.

### Tareeka 2 — Tag push karo

```bash
git tag v1.0.1
git push origin v1.0.1
```

Workflow khud:

1. Release APK build karta hai
2. **Sign** karta hai (bina iske install nahi hota)
3. Verify karta hai ki signature sach me lagi
4. Changelog banata hai
5. Release create karke APK attach karta hai

---

## "App not installed as package appears to be invalid"

Ye error tab aata hai jab APK **unsigned** ho. Android bina digital signature ke
koi bhi app install nahi karta.

Ab workflow ye khud handle karta hai:

- `RELEASE_KEYSTORE_BASE64` secret mile to usse sign karta hai
- Nahi mile to ek temporary keystore bana kar sign kar deta hai
- Signature verify bhi karta hai, warna build fail ho jaata hai

### Doosri wajahein (agar phir bhi error aaye)

| Wajah | Fix |
| --- | --- |
| Purana Jarvis pehle se installed hai, signature alag hai | Purana app **uninstall** karke dobara install karo |
| APK download adhoora raha | Dobara download karo |
| Phone Android 7 se purana hai | minSdk 24 chahiye |

---

## Permanent keystore (recommended)

Bina secret ke har build me nayi keystore banti hai, matlab har baar purana app
uninstall karna padega. Ek permanent keystore bana lo — ek hi baar ka kaam hai.

### Step 1 — Keystore banao (apne laptop pe)

```bash
keytool -genkeypair \
  -keystore jarvis.keystore \
  -alias jarvis \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass MERA_PASSWORD \
  -keypass MERA_PASSWORD \
  -dname "CN=Rehan, OU=Dev, O=Jarvis, L=Jaipur, ST=Rajasthan, C=IN"
```

> `keytool` Java ke saath aata hai. Android Studio installed hai to already available hai.

### Step 2 — Base64 me convert karo

```bash
# Mac / Linux
base64 -i jarvis.keystore | tr -d '\n' > keystore.txt

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("jarvis.keystore")) > keystore.txt
```

### Step 3 — GitHub me secrets daalo

Repo -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**

| Secret name | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `keystore.txt` ka poora content |
| `RELEASE_KEYSTORE_PASSWORD` | `MERA_PASSWORD` |
| `RELEASE_KEY_ALIAS` | `jarvis` |
| `RELEASE_KEY_PASSWORD` | `MERA_PASSWORD` |

`jarvis.keystore` file ko safe rakho aur **kabhi git me commit mat karo**
(`.gitignore` me already hai). Ye kho gayi to app update kabhi nahi kar paoge.

---

## Gemini API key (optional)

Release APK me key already ho, iske liye:

`Settings` -> `Secrets and variables` -> `Actions` -> naya secret `GEMINI_API_KEY`

> Repo public hai. Ye secret tabhi daalo jab APK sirf khud use karo —
> kisi ko APK doge to wo key nikal sakta hai.

Bina secret ke bhi build chalega, bas app me key blank rahegi (app khud bata dega).

---

## Version number kaise chunein

| Kya badla | Naya tag |
| --- | --- |
| Chhota bug fix | `v1.0.1` |
| Naya feature | `v1.1.0` |
| Bada redesign | `v2.0.0` |

Ek hi tag dobara use nahi kar sakte — hamesha naya number lo.

## Har push pe build check

`Android CI` workflow har push aur PR pe debug APK build karta hai.
Actions tab me green tick ho tabhi release banao.

Debug APK bhi mil jaata hai: **Actions** -> koi bhi run -> **Artifacts**.
