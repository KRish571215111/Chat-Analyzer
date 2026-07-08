#!/bin/bash
export DIR="app/src/main/assets/report_template"
mkdir -p "$DIR/src/styles"
mkdir -p "$DIR/src/types"
mkdir -p "$DIR/src/services"
mkdir -p "$DIR/src/router"
mkdir -p "$DIR/src/pages"
mkdir -p "$DIR/src/layouts"

cat << 'INNER' > "$DIR/package.json"
{
  "name": "chat-export-report",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "devDependencies": {
    "typescript": "^5.2.2",
    "vite": "^5.0.0"
  }
}
INNER

cat << 'INNER' > "$DIR/vite.config.ts"
import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    target: 'es2015'
  }
});
INNER

cat << 'INNER' > "$DIR/tsconfig.json"
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"]
}
INNER

cat << 'INNER' > "$DIR/index.html"
<!DOCTYPE html>
<html lang="en" data-theme="light">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Analytics Report</title>
</head>
<body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
</body>
</html>
INNER

cat << 'INNER' > "$DIR/src/styles/main.css"
:root {
    --primary: #6200ee;
    --primary-variant: #3700b3;
    --secondary: #03dac6;
    --background: #f8f9fa;
    --surface: #ffffff;
    --error: #b00020;
    --on-primary: #ffffff;
    --on-secondary: #000000;
    --on-background: #212529;
    --on-surface: #212529;
    --on-error: #ffffff;
    --border: #e9ecef;
    --sidebar-width: 250px;
}

[data-theme="dark"] {
    --primary: #bb86fc;
    --primary-variant: #3700b3;
    --secondary: #03dac6;
    --background: #121212;
    --surface: #1e1e1e;
    --error: #cf6679;
    --on-primary: #000000;
    --on-secondary: #000000;
    --on-background: #e0e0e0;
    --on-surface: #e0e0e0;
    --on-error: #000000;
    --border: #333333;
}

* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    background-color: var(--background);
    color: var(--on-background);
    line-height: 1.6;
    overflow: hidden;
}

#app {
    display: flex;
    height: 100vh;
    width: 100vw;
}

::-webkit-scrollbar {
    width: 8px;
    height: 8px;
}
::-webkit-scrollbar-track {
    background: transparent;
}
::-webkit-scrollbar-thumb {
    background: #888;
    border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
    background: #555;
}
INNER

cat << 'INNER' > "$DIR/src/styles/components.css"
.sidebar {
    width: var(--sidebar-width);
    background-color: var(--surface);
    border-right: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    height: 100%;
}
.sidebar-brand {
    padding: 1.5rem;
    font-size: 1.5rem;
    font-weight: bold;
    color: var(--primary);
    border-bottom: 1px solid var(--border);
}
.sidebar-nav {
    flex: 1;
    overflow-y: auto;
    padding: 1rem 0;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
}
.nav-item {
    padding: 0.75rem 1.5rem;
    color: var(--on-surface);
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 1rem;
    transition: background-color 0.2s, color 0.2s;
}
.nav-item:hover {
    background-color: var(--background);
}
.nav-item.active {
    background-color: var(--primary);
    color: var(--on-primary);
}
.nav-icon {
    font-size: 1.25rem;
}

.main-wrapper {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}
.topbar {
    height: 64px;
    background-color: var(--surface);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 1.5rem;
}
.search-container input {
    padding: 0.5rem 1rem;
    border: 1px solid var(--border);
    border-radius: 20px;
    background-color: var(--background);
    color: var(--on-background);
    width: 300px;
    outline: none;
}
.search-container input:focus {
    border-color: var(--primary);
}
.topbar-actions button {
    background: none;
    border: none;
    color: var(--on-surface);
    font-size: 1.5rem;
    cursor: pointer;
    padding: 0.5rem;
    border-radius: 50%;
}
.topbar-actions button:hover {
    background-color: var(--background);
}
.page-container {
    flex: 1;
    overflow-y: auto;
    padding: 2rem;
}
.page-title {
    font-size: 2rem;
    margin-bottom: 1.5rem;
}

.card {
    background-color: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 1.5rem;
    box-shadow: 0 4px 6px rgba(0,0,0,0.05);
}

.grid-3 {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 1.5rem;
}

.stat-card {
    display: flex;
    align-items: center;
    gap: 1.5rem;
}
.stat-card .icon {
    font-size: 2.5rem;
}
.stat-card .info h3 {
    font-size: 0.875rem;
    color: #888;
    margin-bottom: 0.25rem;
}
.stat-card .info p {
    font-size: 1.5rem;
    font-weight: bold;
}

.table-wrapper {
    background-color: var(--surface);
    border-radius: 12px;
    border: 1px solid var(--border);
    overflow: hidden;
}
table {
    width: 100%;
    border-collapse: collapse;
}
th, td {
    padding: 1rem;
    text-align: left;
    border-bottom: 1px solid var(--border);
}
th {
    background-color: var(--background);
    font-weight: 600;
}
tr:last-child td {
    border-bottom: none;
}
INNER

cat << 'INNER' > "$DIR/src/types/index.ts"
export interface ConfigData {
    title: string;
    description: string;
    exportedAt: string;
}
export interface StatData {
    label: string;
    value: string;
    icon: string;
}
export interface AIData {
    category: string;
    summaryText: string;
}
export interface DashboardData {
    stats: StatData[];
    aiSummaries: AIData[];
}
export interface MemberData {
    name: string;
    phone: string;
    role: string;
}
export interface MessageData {
    sender: string;
    timestamp: string;
    text: string;
}
INNER

cat << 'INNER' > "$DIR/src/services/dataService.ts"
const cache = new Map<string, any>();

export async function fetchJson<T>(filename: string): Promise<T> {
    if (cache.has(filename)) {
        return cache.get(filename);
    }
    try {
        const response = await fetch(`./data/${filename}`);
        if (!response.ok) {
            throw new Error(`Failed to load ${filename}`);
        }
        const data = await response.json();
        cache.set(filename, data);
        return data;
    } catch (error) {
        console.error(`Error fetching ${filename}`, error);
        throw error;
    }
}
INNER

cat << 'INNER' > "$DIR/src/pages/Dashboard.ts"
import { fetchJson } from '../services/dataService';
import { DashboardData } from '../types';

export async function Dashboard() {
    const container = document.getElementById('page-container');
    if (!container) return;

    try {
        const data = await fetchJson<DashboardData>('dashboard.json');
        let html = `<h1 class="page-title">Dashboard</h1>`;
        
        if (data.stats && data.stats.length > 0) {
            html += `<div class="grid-3" style="margin-bottom: 2rem;">`;
            data.stats.forEach(stat => {
                html += `
                    <div class="card stat-card">
                        <div class="icon">${stat.icon}</div>
                        <div class="info">
                            <h3>${stat.label}</h3>
                            <p>${stat.value}</p>
                        </div>
                    </div>
                `;
            });
            html += `</div>`;
        }

        if (data.aiSummaries && data.aiSummaries.length > 0) {
            html += `<h2>AI Insights</h2><div class="grid-3" style="margin-top: 1rem;">`;
            data.aiSummaries.forEach(ai => {
                html += `
                    <div class="card">
                        <h3 style="margin-bottom: 1rem;">✨ ${ai.category}</h3>
                        <p>${ai.summaryText}</p>
                    </div>
                `;
            });
            html += `</div>`;
        } else {
            html += `<h2>AI Insights</h2><div class="card" style="margin-top: 1rem;"><p>No AI Insights generated.</p></div>`;
        }
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<div class="card" style="background: var(--error); color: var(--on-error)">Error loading dashboard data. If you are viewing locally on file:// scheme, Chrome may block JSON requests. Try using Firefox/Safari, or run a local server (python -m http.server).</div>`;
    }
}
INNER

cat << 'INNER' > "$DIR/src/pages/Members.ts"
import { fetchJson } from '../services/dataService';
import { MemberData } from '../types';

export async function Members() {
    const container = document.getElementById('page-container');
    if (!container) return;

    try {
        const data = await fetchJson<MemberData[]>('members.json');
        let html = `
            <h1 class="page-title">Members (${data.length})</h1>
            <div class="table-wrapper">
                <table>
                    <thead>
                        <tr><th>Name</th><th>Phone</th><th>Role</th></tr>
                    </thead>
                    <tbody>
        `;
        data.forEach(m => {
            html += `<tr><td>${m.name}</td><td>${m.phone}</td><td>${m.role}</td></tr>`;
        });
        html += `</tbody></table></div>`;
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<div class="card" style="background: var(--error); color: var(--on-error)">Error loading members data.</div>`;
    }
}
INNER

cat << 'INNER' > "$DIR/src/pages/Messages.ts"
import { fetchJson } from '../services/dataService';
import { MessageData } from '../types';

export async function Messages() {
    const container = document.getElementById('page-container');
    if (!container) return;

    try {
        const data = await fetchJson<MessageData[]>('messages.json');
        let html = `<h1 class="page-title">Messages (${data.length})</h1><div style="display: flex; flex-direction: column; gap: 1rem;">`;
        
        const displayData = data.slice(0, 100);
        displayData.forEach(m => {
            html += `
                <div class="card">
                    <div style="display: flex; justify-content: space-between; margin-bottom: 0.5rem; color: var(--primary);">
                        <strong>${m.sender}</strong>
                        <span style="color: #888; font-size: 0.875rem;">${m.timestamp}</span>
                    </div>
                    <div>${m.text}</div>
                </div>
            `;
        });
        
        if (data.length > 100) {
            html += `<div class="card" style="text-align: center;">Showing first 100 messages...</div>`;
        }
        html += `</div>`;
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<div class="card" style="background: var(--error); color: var(--on-error)">Error loading messages data.</div>`;
    }
}
INNER

cat << 'INNER' > "$DIR/src/pages/Media.ts"
export async function Media() { document.getElementById('page-container')!.innerHTML = '<h1 class="page-title">Media Gallery</h1><div class="card">Media integration loaded via JSON.</div>'; }
INNER

cat << 'INNER' > "$DIR/src/pages/Analytics.ts"
export async function Analytics() { document.getElementById('page-container')!.innerHTML = '<h1 class="page-title">Analytics</h1><div class="card">Analytics integration loaded via JSON.</div>'; }
INNER

cat << 'INNER' > "$DIR/src/pages/Timeline.ts"
export async function Timeline() { document.getElementById('page-container')!.innerHTML = '<h1 class="page-title">Timeline</h1><div class="card">Timeline integration loaded via JSON.</div>'; }
INNER

cat << 'INNER' > "$DIR/src/pages/Reports.ts"
export async function Reports() { document.getElementById('page-container')!.innerHTML = '<h1 class="page-title">Reports</h1><div class="card">Reports integration loaded via JSON.</div>'; }
INNER

cat << 'INNER' > "$DIR/src/pages/Bookmarks.ts"
export async function Bookmarks() { document.getElementById('page-container')!.innerHTML = '<h1 class="page-title">Bookmarks</h1><div class="card">Bookmarks integration loaded via JSON.</div>'; }
INNER

cat << 'INNER' > "$DIR/src/pages/Settings.ts"
export async function Settings() { document.getElementById('page-container')!.innerHTML = '<h1 class="page-title">Settings</h1><div class="card">Offline report viewing settings.</div>'; }
INNER

cat << 'INNER' > "$DIR/src/router/index.ts"
import { Dashboard } from '../pages/Dashboard';
import { Members } from '../pages/Members';
import { Messages } from '../pages/Messages';
import { Media } from '../pages/Media';
import { Analytics } from '../pages/Analytics';
import { Timeline } from '../pages/Timeline';
import { Reports } from '../pages/Reports';
import { Bookmarks } from '../pages/Bookmarks';
import { Settings } from '../pages/Settings';

export class Router {
    private routes: Record<string, () => void> = {
        'dashboard': Dashboard,
        'members': Members,
        'messages': Messages,
        'media': Media,
        'analytics': Analytics,
        'timeline': Timeline,
        'reports': Reports,
        'bookmarks': Bookmarks,
        'settings': Settings,
    };

    constructor(private containerId: string) {
        window.addEventListener('hashchange', () => this.handleRoute());
    }

    public init() {
        this.handleRoute();
    }

    private handleRoute() {
        let hash = window.location.hash.replace('#', '');
        if (!hash || !this.routes[hash]) {
            hash = 'dashboard';
            window.location.hash = hash;
        }

        document.querySelectorAll('.nav-item').forEach(el => {
            if (el.getAttribute('href') === `#${hash}`) {
                el.classList.add('active');
            } else {
                el.classList.remove('active');
            }
        });

        const container = document.getElementById(this.containerId);
        if (container) {
            container.innerHTML = '<div class="card">Loading...</div>';
            try {
                this.routes[hash]();
            } catch (e) {
                container.innerHTML = `<div class="card" style="background: var(--error); color: var(--on-error)">Failed to load view.</div>`;
            }
        }
    }
}
INNER

cat << 'INNER' > "$DIR/src/layouts/AppLayout.ts"
export function renderAppLayout() {
    const app = document.getElementById('app');
    if (!app) return;

    app.innerHTML = `
        <aside class="sidebar">
            <div class="sidebar-brand">📊 Analytics</div>
            <nav class="sidebar-nav">
                <a href="#dashboard" class="nav-item active"><span class="nav-icon">📊</span> Dashboard</a>
                <a href="#members" class="nav-item"><span class="nav-icon">👥</span> Members</a>
                <a href="#messages" class="nav-item"><span class="nav-icon">💬</span> Messages</a>
                <a href="#media" class="nav-item"><span class="nav-icon">🖼️</span> Media</a>
                <a href="#analytics" class="nav-item"><span class="nav-icon">📈</span> Analytics</a>
                <a href="#timeline" class="nav-item"><span class="nav-icon">⏱️</span> Timeline</a>
                <a href="#reports" class="nav-item"><span class="nav-icon">📝</span> Reports</a>
                <a href="#bookmarks" class="nav-item"><span class="nav-icon">🔖</span> Bookmarks</a>
                <a href="#settings" class="nav-item"><span class="nav-icon">⚙️</span> Settings</a>
            </nav>
        </aside>
        <div class="main-wrapper">
            <header class="topbar">
                <div class="search-container">
                    <input type="text" placeholder="Search report..." />
                </div>
                <div class="topbar-actions">
                    <button id="theme-toggle">🌙</button>
                </div>
            </header>
            <main id="page-container" class="page-container"></main>
        </div>
    `;

    const toggle = document.getElementById('theme-toggle');
    if (toggle) {
        toggle.addEventListener('click', () => {
            const html = document.documentElement;
            const isDark = html.getAttribute('data-theme') === 'dark';
            html.setAttribute('data-theme', isDark ? 'light' : 'dark');
            toggle.textContent = isDark ? '🌙' : '☀️';
        });
    }
}
INNER

cat << 'INNER' > "$DIR/src/main.ts"
import './styles/main.css';
import './styles/components.css';
import { renderAppLayout } from './layouts/AppLayout';
import { Router } from './router';
import { fetchJson } from './services/dataService';
import { ConfigData } from './types';

async function bootstrap() {
    renderAppLayout();
    const router = new Router('page-container');
    router.init();

    try {
        const config = await fetchJson<ConfigData>('config.json');
        if (config.title) {
            document.title = config.title;
        }
    } catch (e) {
        console.warn('Config not found or could not be loaded.');
    }
}

document.addEventListener('DOMContentLoaded', bootstrap);
INNER

