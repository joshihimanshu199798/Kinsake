# Kinsake

A local-first family memory journal built with React, TypeScript and Vite.

## Privacy
Photos, voice recordings and profile data are stored in IndexedDB in the browser on the current device. They are not uploaded or synced. Export backups before clearing browser data. Backup files contain private media and should be kept secure.

## Features in the starter
- Family dashboard and responsive navigation
- Local photo library
- Child profile and age calculation
- Milestone journal
- Browser voice recording and playback
- Local family vault
- JSON backup and restore
- Light and dark appearance

Cloud sync, guest sharing, AI tagging, image editing and encrypted cloud storage are not included yet.

## Run locally
Requires Node.js 20+.

```bash
npm install
npm run dev
npm run build
```

## GitHub Pages
The included GitHub Actions workflow builds and deploys the `main` branch. Set **Settings → Pages → Build and deployment → GitHub Actions**. The project site URL will be `https://YOUR-USERNAME.github.io/Kinsake/`.
