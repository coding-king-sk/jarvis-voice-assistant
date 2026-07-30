# Automatic Release

Release ab apne aap ban jaati hai. Do tareeke hain.

## Tareeka 1 — Tag push karo (recommended)

```bash
git tag v1.0.0
git push origin v1.0.0
```

Bas. GitHub Actions khud:

1. Release APK build karega
2. Pichle tag se ab tak ka changelog banayega
3. GitHub Release create karega
4. APK usme attach kar dega

## Tareeka 2 — GitHub website se (bina terminal)

1. Repo kholo -> **Actions** tab
2. Left side me **Release** workflow choose karo
3. **Run workflow** dabao
4. Version daalo, jaise `v1.0.0`
5. **Run workflow** confirm karo

~5 minute me release ready.

## Gemini API key (optional)

Agar chaho ki release APK me key already hui ho:

1. Repo -> **Settings** -> **Secrets and variables** -> **Actions**
2. **New repository secret**
3. Name: `GEMINI_API_KEY`, Value: apni key
4. **Add secret**

> Public repo hai, isliye ye tabhi karo jab APK sirf khud use karna ho.
> Kisi ko APK dena ho to secret mat daalo — log key nikal sakte hain.

Bina secret ke bhi workflow chalega, bas APK me key blank rahegi.

## Version number kaise chunein

| Kya badla | Naya tag |
| --- | --- |
| Chhota bug fix | `v1.0.1` |
| Naya feature | `v1.1.0` |
| Bada redesign | `v2.0.0` |

## Har push pe build check

`Android CI` workflow har push aur PR pe debug APK build karta hai.
Agar code toota hoga to Actions tab me laal cross dikhega — release banane se
pehle wahan green tick check kar lena.

Debug APK bhi download kar sakte ho: **Actions** -> koi bhi run -> **Artifacts**.

## Signing (baad me, Play Store ke liye)

Abhi release APK unsigned/debug-signed hai — personal use ke liye theek hai.
Play Store pe daalna ho to keystore banakar `signingConfigs` add karna hoga
aur keystore ko base64 secret ki tarah GitHub me rakhna hoga.
