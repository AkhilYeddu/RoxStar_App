import os

html_content = r'''<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>RoxStar | Voice Drafts, Real-Time Rooms & Spin Arena</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap" rel="stylesheet">
  <script src="/socket.io/socket.io.js"></script>
  <style>
    :root {
      --bg-base: #07090f;
      --bg-surface: #0e131f;
      --bg-card: #151b2c;
      --bg-card-hover: #1c243a;
      --primary: #6366f1;
      --primary-hover: #4f46e5;
      --primary-glow: rgba(99, 102, 241, 0.25);
      --accent-pink: #ec4899;
      --accent-cyan: #06b6d4;
      --accent-amber: #f59e0b;
      --accent-emerald: #10b981;
      --accent-red: #ef4444;
      --text-main: #f8fafc;
      --text-muted: #94a3b8;
      --border: rgba(255, 255, 255, 0.08);
      --border-focus: rgba(99, 102, 241, 0.5);
      --glass: rgba(18, 24, 38, 0.75);
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      font-family: 'Plus Jakarta Sans', sans-serif;
    }

    body {
      background-color: var(--bg-base);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      overflow-x: hidden;
    }

    .glow-ambient {
      position: fixed;
      top: -150px;
      left: 50%;
      transform: translateX(-50%);
      width: 1100px;
      height: 500px;
      background: radial-gradient(circle, rgba(99, 102, 241, 0.18) 0%, rgba(236, 72, 153, 0.1) 45%, transparent 70%);
      pointer-events: none;
      z-index: 0;
    }

    /* Header */
    header {
      position: sticky;
      top: 0;
      z-index: 100;
      background: rgba(7, 9, 15, 0.85);
      backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--border);
      padding: 0.85rem 2rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      text-decoration: none;
    }

    .brand-badge {
      background: linear-gradient(135deg, var(--primary), var(--accent-pink));
      color: white;
      font-weight: 800;
      font-size: 1.15rem;
      padding: 0.35rem 0.8rem;
      border-radius: 12px;
      letter-spacing: -0.5px;
      box-shadow: 0 4px 15px rgba(99, 102, 241, 0.35);
    }

    .brand-title-wrap h1 {
      font-size: 1.15rem;
      font-weight: 800;
      letter-spacing: -0.3px;
      background: linear-gradient(to right, #ffffff, #cbd5e1);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .brand-title-wrap p {
      font-size: 0.7rem;
      color: var(--text-muted);
      font-weight: 500;
      letter-spacing: 0.2px;
    }

    /* Nav Tabs */
    .nav-tabs {
      display: flex;
      gap: 0.35rem;
      background: var(--bg-surface);
      padding: 0.3rem;
      border-radius: 12px;
      border: 1px solid var(--border);
    }

    .nav-tab {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.5rem 1rem;
      border-radius: 8px;
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 0.825rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .nav-tab:hover {
      color: var(--text-main);
      background: rgba(255, 255, 255, 0.04);
    }

    .nav-tab.active {
      background: var(--primary);
      color: white;
      box-shadow: 0 4px 12px var(--primary-glow);
    }

    /* Header Meta */
    .header-status {
      display: flex;
      align-items: center;
      gap: 1rem;
      font-size: 0.8rem;
    }

    .pill {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.35rem 0.75rem;
      border-radius: 9999px;
      font-size: 0.725rem;
      font-weight: 600;
      border: 1px solid var(--border);
      background: var(--bg-surface);
    }

    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
    }
    .dot-green { background: var(--accent-emerald); box-shadow: 0 0 8px var(--accent-emerald); }
    .dot-amber { background: var(--accent-amber); box-shadow: 0 0 8px var(--accent-amber); }
    .dot-red { background: var(--accent-red); box-shadow: 0 0 8px var(--accent-red); }

    /* Main Container */
    main {
      position: relative;
      z-index: 10;
      max-width: 1400px;
      width: 100%;
      margin: 0 auto;
      padding: 2rem;
      flex: 1;
    }

    .tab-content {
      display: none;
      animation: fadeIn 0.25s ease-out;
    }

    .tab-content.active {
      display: block;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(6px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Card System */
    .card {
      background: var(--glass);
      backdrop-filter: blur(16px);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 1.5rem;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
    }

    .card-title {
      font-size: 0.95rem;
      font-weight: 700;
      letter-spacing: -0.2px;
      margin-bottom: 1.25rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: #e2e8f0;
    }

    .card-title .badge {
      font-size: 0.7rem;
      font-weight: 600;
      padding: 0.2rem 0.5rem;
      border-radius: 6px;
      background: rgba(99, 102, 241, 0.15);
      color: #a5b4fc;
      border: 1px solid rgba(99, 102, 241, 0.3);
    }

    /* Buttons */
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.45rem;
      padding: 0.65rem 1.1rem;
      border-radius: 10px;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      border: none;
      transition: all 0.2s;
      outline: none;
    }

    .btn:active {
      transform: scale(0.98);
    }

    .btn-primary {
      background: var(--primary);
      color: white;
    }
    .btn-primary:hover { background: var(--primary-hover); box-shadow: 0 4px 14px var(--primary-glow); }

    .btn-accent {
      background: linear-gradient(135deg, var(--accent-pink), #db2777);
      color: white;
      box-shadow: 0 4px 14px rgba(236, 72, 153, 0.3);
    }
    .btn-accent:hover { opacity: 0.95; transform: translateY(-1px); }

    .btn-secondary {
      background: var(--bg-surface);
      color: var(--text-main);
      border: 1px solid var(--border);
    }
    .btn-secondary:hover { background: var(--bg-card-hover); border-color: rgba(255,255,255,0.15); }

    .btn-danger {
      background: rgba(239, 68, 68, 0.15);
      color: #fca5a5;
      border: 1px solid rgba(239, 68, 68, 0.3);
    }
    .btn-danger:hover { background: rgba(239, 68, 68, 0.25); color: #fff; }

    .btn-sm {
      padding: 0.4rem 0.75rem;
      font-size: 0.775rem;
      border-radius: 8px;
    }

    .btn-icon {
      width: 32px;
      height: 32px;
      padding: 0;
      border-radius: 8px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    /* Form Inputs */
    .input-field {
      width: 100%;
      background: var(--bg-surface);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 0.65rem 0.9rem;
      color: var(--text-main);
      font-size: 0.85rem;
      outline: none;
      transition: all 0.2s;
    }

    .input-field:focus {
      border-color: var(--primary);
      box-shadow: 0 0 0 2px var(--primary-glow);
    }

    /* Grid Layouts */
    .grid-2col {
      display: grid;
      grid-template-columns: 420px 1fr;
      gap: 2rem;
    }

    @media (max-width: 1024px) {
      .grid-2col {
        grid-template-columns: 1fr;
      }
    }

    /* ========================================================= */
    /* VOICE STUDIO STYLES */
    /* ========================================================= */
    .visualizer-box {
      width: 100%;
      height: 90px;
      background: #090e18;
      border-radius: 12px;
      border: 1px solid var(--border);
      margin-bottom: 1.25rem;
      position: relative;
      overflow: hidden;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .visualizer-canvas {
      width: 100%;
      height: 100%;
    }

    .record-timer {
      position: absolute;
      top: 10px;
      right: 12px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.85rem;
      color: var(--accent-emerald);
      font-weight: 600;
      background: rgba(0,0,0,0.5);
      padding: 0.2rem 0.5rem;
      border-radius: 6px;
      border: 1px solid rgba(16, 185, 129, 0.3);
    }

    .effect-selector-label {
      font-size: 0.775rem;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 0.5rem;
      display: flex;
      justify-content: space-between;
    }

    .effect-chips {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.5rem;
      margin-bottom: 1.25rem;
    }

    .effect-chip {
      padding: 0.55rem 0.5rem;
      font-size: 0.775rem;
      font-weight: 600;
      text-align: center;
      border-radius: 8px;
      background: var(--bg-surface);
      color: var(--text-muted);
      border: 1px solid var(--border);
      cursor: pointer;
      transition: all 0.2s;
    }

    .effect-chip:hover {
      color: var(--text-main);
      border-color: rgba(255,255,255,0.15);
    }

    .effect-chip.active {
      background: rgba(99, 102, 241, 0.15);
      border-color: var(--primary);
      color: #818cf8;
      box-shadow: 0 0 12px rgba(99, 102, 241, 0.2);
    }

    /* Drafts List */
    .drafts-list {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
      max-height: 520px;
      overflow-y: auto;
      padding-right: 0.25rem;
    }

    .drafts-list::-webkit-scrollbar {
      width: 5px;
    }
    .drafts-list::-webkit-scrollbar-thumb {
      background: rgba(255,255,255,0.1);
      border-radius: 10px;
    }

    .draft-item {
      background: var(--bg-surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 0.9rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      transition: border-color 0.2s, background-color 0.2s;
    }

    .draft-item:hover {
      border-color: rgba(255, 255, 255, 0.14);
      background: var(--bg-card-hover);
    }

    .draft-item.playing {
      border-color: var(--primary);
      background: rgba(99, 102, 241, 0.06);
    }

    .draft-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }

    .draft-title-area {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex: 1;
      overflow: hidden;
    }

    .draft-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--text-main);
      cursor: pointer;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .draft-title:hover {
      color: #818cf8;
      text-decoration: underline;
    }

    .draft-edit-input {
      background: var(--bg-base);
      border: 1px solid var(--primary);
      border-radius: 6px;
      padding: 0.2rem 0.5rem;
      font-size: 0.825rem;
      color: white;
      width: 100%;
      outline: none;
    }

    .draft-tags {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .draft-tag {
      font-size: 0.675rem;
      font-weight: 600;
      padding: 0.15rem 0.45rem;
      border-radius: 4px;
      border: 1px solid var(--border);
      background: var(--bg-card);
      color: var(--text-muted);
    }

    .draft-tag-echo {
      color: var(--accent-cyan);
      border-color: rgba(6, 182, 212, 0.3);
      background: rgba(6, 182, 212, 0.1);
    }

    .draft-tag-reverb {
      color: var(--accent-pink);
      border-color: rgba(236, 72, 153, 0.3);
      background: rgba(236, 72, 153, 0.1);
    }

    .draft-player-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .btn-play {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--primary);
      color: white;
      border: none;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      flex-shrink: 0;
      transition: all 0.2s;
    }

    .btn-play:hover {
      background: var(--primary-hover);
      transform: scale(1.05);
    }

    .draft-progress-container {
      flex: 1;
      height: 6px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 9999px;
      position: relative;
      cursor: pointer;
      overflow: hidden;
    }

    .draft-progress-bar {
      height: 100%;
      width: 0%;
      background: linear-gradient(to right, var(--primary), var(--accent-pink));
      border-radius: 9999px;
      transition: width 0.1s linear;
    }

    .draft-duration-text {
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.725rem;
      color: var(--text-muted);
      min-width: 45px;
      text-align: right;
    }

    .draft-actions {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin-left: auto;
    }

    /* ========================================================= */
    /* ROOM TAB STYLES */
    /* ========================================================= */
    .room-header-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      margin-bottom: 1.5rem;
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(236, 72, 153, 0.08));
      border: 1px solid rgba(99, 102, 241, 0.25);
    }

    .room-code-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: var(--bg-surface);
      border: 1px solid var(--border);
      padding: 0.4rem 0.8rem;
      border-radius: 8px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.9rem;
      font-weight: 700;
      color: #cbd5e1;
    }

    .member-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 0.75rem;
      margin-top: 1rem;
    }

    .member-chip {
      background: var(--bg-surface);
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 0.75rem 0.85rem;
      display: flex;
      align-items: center;
      gap: 0.65rem;
    }

    .member-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--primary), var(--accent-pink));
      color: white;
      font-weight: 700;
      font-size: 0.8rem;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .member-info {
      overflow: hidden;
      flex: 1;
    }

    .member-name {
      font-size: 0.825rem;
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .member-role {
      font-size: 0.675rem;
      color: var(--text-muted);
    }

    /* Activity Stream */
    .activity-feed {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-height: 280px;
      overflow-y: auto;
      margin-top: 0.5rem;
    }

    .activity-item {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      padding: 0.6rem 0.75rem;
      background: var(--bg-surface);
      border-radius: 8px;
      font-size: 0.775rem;
      border-left: 3px solid var(--primary);
    }

    .activity-time {
      font-family: 'JetBrains Mono', monospace;
      color: var(--text-muted);
      font-size: 0.7rem;
      flex-shrink: 0;
    }

    /* ========================================================= */
    /* SPIN WHEEL TAB STYLES */
    /* ========================================================= */
    .spin-layout {
      display: grid;
      grid-template-columns: 500px 1fr;
      gap: 2rem;
      align-items: start;
    }

    @media (max-width: 1024px) {
      .spin-layout {
        grid-template-columns: 1fr;
      }
    }

    .wheel-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
    }

    .wheel-stage {
      position: relative;
      width: 440px;
      height: 440px;
      max-width: 100%;
    }

    .wheel-canvas {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      box-shadow: 0 0 35px rgba(99, 102, 241, 0.25);
    }

    .wheel-pointer {
      position: absolute;
      top: -12px;
      left: 50%;
      transform: translateX(-50%);
      width: 0;
      height: 0;
      border-left: 14px solid transparent;
      border-right: 14px solid transparent;
      border-top: 24px solid #ef4444;
      filter: drop-shadow(0 4px 8px rgba(0,0,0,0.6));
      z-index: 20;
    }

    .wheel-center-pin {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 50px;
      height: 50px;
      border-radius: 50%;
      background: radial-gradient(circle, #ffffff, #94a3b8);
      border: 3px solid #1e293b;
      box-shadow: 0 4px 15px rgba(0,0,0,0.5);
      z-index: 15;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.1rem;
    }

    .spin-status-box {
      margin-top: 1.5rem;
      width: 100%;
      background: var(--bg-surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 1rem 1.25rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .spin-timer-val {
      font-family: 'JetBrains Mono', monospace;
      font-size: 1.4rem;
      font-weight: 700;
      color: var(--accent-amber);
    }

    /* Winner Banner */
    .winner-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 9, 15, 0.85);
      backdrop-filter: blur(10px);
      z-index: 200;
      display: none;
      align-items: center;
      justify-content: center;
    }

    .winner-card {
      background: var(--bg-card);
      border: 2px solid var(--accent-amber);
      border-radius: 20px;
      padding: 2.5rem;
      text-align: center;
      max-width: 480px;
      width: 90%;
      box-shadow: 0 0 50px rgba(245, 158, 11, 0.35);
      animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    }

    @keyframes popIn {
      from { transform: scale(0.85); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
    }

    /* ========================================================= */
    /* ABOUT OBOE TAB STYLES */
    /* ========================================================= */
    .oboe-hero {
      background: linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(6, 182, 212, 0.1));
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 16px;
      padding: 2rem;
      margin-bottom: 2rem;
    }

    .oboe-hero h2 {
      font-size: 1.5rem;
      font-weight: 800;
      margin-bottom: 0.5rem;
      background: linear-gradient(to right, #fff, #94a3b8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .pipeline-diagram {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      background: var(--bg-surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 1.5rem 1rem;
      margin: 1.5rem 0;
      overflow-x: auto;
    }

    .diagram-step {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.4rem;
      min-width: 120px;
    }

    .step-icon-wrap {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
      color: #818cf8;
    }

    .diagram-arrow {
      color: var(--text-muted);
      font-size: 1.25rem;
      font-weight: 700;
    }

    .code-snippet {
      background: #090e18;
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 1rem;
      font-family: 'JetBrains Mono', monospace;
      font-size: 0.775rem;
      color: #94a3b8;
      overflow-x: auto;
      line-height: 1.5;
    }

    .step-card {
      background: var(--bg-surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 1.25rem;
      margin-bottom: 1rem;
    }

    .step-card-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.75rem;
    }

    .step-number {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: var(--primary);
      color: white;
      font-size: 0.8rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Modal */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(7, 9, 15, 0.85);
      backdrop-filter: blur(8px);
      z-index: 200;
      display: none;
      align-items: center;
      justify-content: center;
    }

    .modal-box {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 1.75rem;
      max-width: 440px;
      width: 90%;
      box-shadow: 0 10px 40px rgba(0,0,0,0.6);
    }

    .toast {
      position: fixed;
      bottom: 2rem;
      right: 2rem;
      background: #1e293b;
      border: 1px solid var(--primary);
      color: white;
      padding: 0.75rem 1.25rem;
      border-radius: 10px;
      font-size: 0.85rem;
      font-weight: 600;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
      z-index: 300;
      display: none;
      animation: slideUp 0.2s ease-out;
    }

    @keyframes slideUp {
      from { transform: translateY(15px); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
  </style>
</head>
<body>
  <div class="glow-ambient"></div>

  <!-- Header -->
  <header>
    <a href="#" class="brand">
      <div class="brand-badge">RS</div>
      <div class="brand-title-wrap">
        <h1>RoxStar Studio</h1>
        <p>Phase 1 Voice Drafts • Phase 2 Rooms • Phase 3 Spin Arena</p>
      </div>
    </a>

    <!-- Nav Tabs -->
    <div class="nav-tabs">
      <button class="nav-tab active" data-tab="voice-studio" onclick="switchTab('voice-studio')">
        🎙️ Voice Studio
      </button>
      <button class="nav-tab" data-tab="room-section" onclick="switchTab('room-section')">
        🚪 Room & Presence
      </button>
      <button class="nav-tab" data-tab="spin-wheel" onclick="switchTab('spin-wheel')">
        🎡 Spin Wheel
      </button>
      <button class="nav-tab" data-tab="about-oboe" onclick="switchTab('about-oboe')">
        📱 About Oboe
      </button>
    </div>

    <!-- Status Indicators -->
    <div class="header-status">
      <div class="pill">
        <span class="dot dot-green" id="ws-dot"></span>
        <span id="ws-status">Connected</span>
      </div>
      <div class="pill">
        <span style="color: var(--text-muted);">User:</span>
        <span id="user-display" style="color: #cbd5e1; font-weight: 700;">Alex_Vocalist</span>
      </div>
    </div>
  </header>

  <!-- Main Content Area -->
  <main>

    <!-- ========================================================= -->
    <!-- TAB 1: VOICE STUDIO -->
    <!-- ========================================================= -->
    <div id="tab-voice-studio" class="tab-content active">
      <div class="grid-2col">
        <!-- Left: Studio Audio Recorder -->
        <div class="card">
          <div class="card-title">
            <span>Voice Draft Recorder</span>
            <span class="badge">Oboe C++ Spec</span>
          </div>

          <!-- Waveform Visualizer -->
          <div class="visualizer-box">
            <canvas id="recorder-canvas" class="visualizer-canvas"></canvas>
            <div class="record-timer" id="record-timer">00:00.0</div>
          </div>

          <!-- Optional Draft Title -->
          <div style="margin-bottom: 1.25rem;">
            <label style="font-size: 0.775rem; font-weight: 600; color: var(--text-muted); display: block; margin-bottom: 0.4rem;">
              Draft Title (Optional)
            </label>
            <input type="text" id="draft-title-input" class="input-field" placeholder="e.g. Chorus Vocal Harmony Take 1" />
          </div>

          <!-- DSP Effect Selector -->
          <div>
            <div class="effect-selector-label">
              <span>Voice DSP Filter</span>
              <span style="color: var(--primary); font-size: 0.725rem;">Real-Time Audio DSP</span>
            </div>
            <div class="effect-chips">
              <button type="button" class="effect-chip active" data-effect="NONE" onclick="selectEffect('NONE')">
                Clean (None)
              </button>
              <button type="button" class="effect-chip" data-effect="ECHO" onclick="selectEffect('ECHO')">
                Echo (Delay)
              </button>
              <button type="button" class="effect-chip" data-effect="REVERB" onclick="selectEffect('REVERB')">
                Reverb (Room)
              </button>
            </div>
          </div>

          <!-- Record Controls -->
          <div style="display: flex; gap: 0.75rem; margin-top: 1rem;">
            <button id="btn-record-main" class="btn btn-accent" style="flex: 1;" onclick="toggleRecording()">
              🔴 Start Recording
            </button>
            <button id="btn-discard" class="btn btn-secondary" style="display: none;" onclick="discardRecording()">
              ❌ Discard
            </button>
          </div>

          <div style="margin-top: 1.25rem; font-size: 0.75rem; color: var(--text-muted); line-height: 1.4; border-top: 1px solid var(--border); padding-top: 0.85rem;">
            💡 <strong>Native Spec:</strong> On Android, Oboe captures microphone audio via AAudio stream directly into C++ circular buffers and applies DSP before writing RIFF WAV format. In browser demo, real Web Audio is recorded!
          </div>
        </div>

        <!-- Right: My Voice Drafts List -->
        <div class="card">
          <div class="card-title">
            <span>My Voice Drafts</span>
            <span class="badge" id="drafts-count-badge">0 Drafts</span>
          </div>

          <div id="drafts-list" class="drafts-list">
            <!-- Rendered by JS -->
          </div>
        </div>
      </div>
    </div>

    <!-- ========================================================= -->
    <!-- TAB 2: ROOM & PRESENCE -->
    <!-- ========================================================= -->
    <div id="tab-room-section" class="tab-content">
      <!-- Active Room Banner -->
      <div class="card room-header-card">
        <div>
          <div style="font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.8px; color: var(--accent-cyan); font-weight: 700; margin-bottom: 0.25rem;">
            Active Room Session
          </div>
          <h2 id="room-display-name" style="font-size: 1.35rem; font-weight: 800; color: white;">
            RoxStar Audio Jam & Spin Arena
          </h2>
          <div style="display: flex; align-items: center; gap: 1rem; margin-top: 0.4rem; font-size: 0.8rem; color: var(--text-muted);">
            <span>Status: <strong id="room-status-badge" style="color: var(--accent-emerald);">IDLE</strong></span>
            <span>•</span>
            <span>Host: <strong id="room-host-badge" style="color: white;">user_alex</strong></span>
          </div>
        </div>

        <div style="display: flex; align-items: center; gap: 0.75rem;">
          <div class="room-code-badge">
            <span style="color: var(--text-muted); font-size: 0.75rem;">CODE:</span>
            <span id="room-code-text">room_studio_alpha</span>
            <button class="btn btn-secondary btn-sm" style="padding: 0.25rem 0.5rem;" onclick="copyRoomCode()">
              📋 Copy
            </button>
          </div>
          <button class="btn btn-danger btn-sm" onclick="leaveCurrentRoom()">
            🚪 Leave
          </button>
        </div>
      </div>

      <div class="grid-2col">
        <!-- Left: Room Management & Participants -->
        <div>
          <!-- Create / Join Room Forms -->
          <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-title">
              <span>Switch or Create Room</span>
            </div>

            <div style="display: flex; flex-direction: column; gap: 1rem;">
              <!-- Join by Code -->
              <div>
                <label style="font-size: 0.775rem; font-weight: 600; color: var(--text-muted); display: block; margin-bottom: 0.35rem;">
                  Join Room by Code
                </label>
                <div style="display: flex; gap: 0.5rem;">
                  <input type="text" id="join-room-input" class="input-field" placeholder="Enter Room ID (e.g. room_studio_alpha)" />
                  <button class="btn btn-primary btn-sm" onclick="joinRoomFromInput()">
                    Join
                  </button>
                </div>
              </div>

              <div style="display: flex; align-items: center; gap: 0.5rem; color: var(--text-muted); font-size: 0.75rem;">
                <div style="flex: 1; height: 1px; background: var(--border);"></div>
                <span>OR</span>
                <div style="flex: 1; height: 1px; background: var(--border);"></div>
              </div>

              <!-- Create New Room -->
              <div>
                <label style="font-size: 0.775rem; font-weight: 600; color: var(--text-muted); display: block; margin-bottom: 0.35rem;">
                  Create Brand New Room
                </label>
                <div style="display: flex; gap: 0.5rem;">
                  <input type="text" id="create-room-name-input" class="input-field" placeholder="e.g. Acoustic Sessions" />
                  <button class="btn btn-accent btn-sm" onclick="createNewRoom()">
                    Create
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Connected Participants -->
          <div class="card">
            <div class="card-title">
              <span>Connected Participants</span>
              <span class="badge" id="member-count-badge">0 / 20</span>
            </div>
            <div id="room-members-grid" class="member-grid">
              <!-- Rendered by JS -->
            </div>
          </div>
        </div>

        <!-- Right: Shared Room Drafts & Activity Feed -->
        <div>
          <!-- Shared Drafts in Room -->
          <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-title">
              <span>Shared Drafts in this Room</span>
              <span class="badge" id="shared-drafts-badge">0 Shared</span>
            </div>
            <div id="shared-drafts-list" class="drafts-list" style="max-height: 260px;">
              <!-- Rendered by JS -->
            </div>
          </div>

          <!-- Real-Time Activity Log -->
          <div class="card">
            <div class="card-title">
              <span>Real-Time Activity Stream</span>
              <span class="badge" style="background: rgba(16, 185, 129, 0.15); color: #34d399;">LIVE</span>
            </div>
            <div id="activity-feed" class="activity-feed">
              <!-- Rendered by JS -->
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========================================================= -->
    <!-- TAB 3: SPIN WHEEL ARENA -->
    <!-- ========================================================= -->
    <div id="tab-spin-wheel" class="tab-content">
      <div class="spin-layout">
        <!-- Left: Canvas Spin Wheel -->
        <div class="card wheel-container">
          <div class="wheel-stage">
            <div class="wheel-pointer"></div>
            <canvas id="wheel-canvas" width="440" height="440" class="wheel-canvas"></canvas>
            <div class="wheel-center-pin">⭐</div>
          </div>

          <div class="spin-status-box">
            <div>
              <div style="font-size: 0.75rem; color: var(--text-muted); font-weight: 600;">STATUS</div>
              <div id="spin-status-label" style="font-size: 0.95rem; font-weight: 700; color: white;">Ready to Spin</div>
            </div>
            <div style="text-align: right;">
              <div style="font-size: 0.75rem; color: var(--text-muted); font-weight: 600;">INTERVAL</div>
              <div id="spin-countdown-val" class="spin-timer-val">5.0s</div>
            </div>
          </div>

          <div style="display: flex; gap: 0.75rem; width: 100%; margin-top: 1rem;">
            <button id="btn-start-spin" class="btn btn-accent" style="flex: 1; font-size: 0.95rem; padding: 0.85rem;" onclick="startSpin()">
              🎡 Start Elimination Spin
            </button>
            <button class="btn btn-secondary" onclick="addDemoParticipants()" title="Add test participants if you are alone in room">
              ➕ Add Demo Players
            </button>
          </div>
        </div>

        <!-- Right: Elimination Leaderboard & Rules -->
        <div>
          <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-title">
              <span>Arena Contenders</span>
              <span class="badge" id="contenders-badge">4 Contenders</span>
            </div>
            <div id="spin-participants-list" style="display: flex; flex-direction: column; gap: 0.65rem;">
              <!-- Rendered by JS -->
            </div>
          </div>

          <div class="card">
            <div class="card-title">
              <span>Authoritative Rules (5-Second Loop)</span>
            </div>
            <ul style="font-size: 0.8rem; color: var(--text-muted); line-height: 1.6; padding-left: 1.25rem;">
              <li><strong>Authoritative Server Loop:</strong> The Node.js spin engine controls all timers and RNG on the backend.</li>
              <li><strong>Strict 5-Second Interval:</strong> Every 5,000ms, exactly 1 participant is selected and eliminated.</li>
              <li><strong>Real-time Broadcast:</strong> Socket.IO broadcasts <code style="color: #cbd5e1;">spin_started</code>, <code style="color: #cbd5e1;">user_eliminated</code>, and <code style="color: #cbd5e1;">spin_ended</code> to all connected clients.</li>
              <li><strong>Persistence:</strong> Spin history and final winner are recorded in the database.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <!-- ========================================================= -->
    <!-- TAB 4: ABOUT OBOE (NATIVE AUDIO GUIDE) -->
    <!-- ========================================================= -->
    <div id="tab-about-oboe" class="tab-content">
      <div class="oboe-hero">
        <h2>How RoxStar Uses Google Oboe for Native Android Audio</h2>
        <p style="color: var(--text-muted); font-size: 0.9rem; line-height: 1.5; max-width: 800px;">
          Standard Android audio in Java/Kotlin (<code style="color: #a5b4fc;">AudioRecord</code> / <code style="color: #a5b4fc;">AudioTrack</code>) suffers from 30ms-100ms latency and garbage collection interruptions. RoxStar uses Google's C++20 Oboe library to achieve sub-10ms latency, direct hardware buffer access, and real-time DSP effects.
        </p>

        <!-- Pipeline Diagram -->
        <div class="pipeline-diagram">
          <div class="diagram-step">
            <div class="step-icon-wrap">🎙️</div>
            <strong style="font-size: 0.8rem;">Android Mic</strong>
            <span style="font-size: 0.7rem; color: var(--text-muted);">48kHz / Mono PCM</span>
          </div>
          <div class="diagram-arrow">➔</div>
          <div class="diagram-step">
            <div class="step-icon-wrap">⚡</div>
            <strong style="font-size: 0.8rem;">Oboe AAudio</strong>
            <span style="font-size: 0.7rem; color: var(--text-muted);">Exclusive Mode Stream</span>
          </div>
          <div class="diagram-arrow">➔</div>
          <div class="diagram-step">
            <div class="step-icon-wrap">⚙️</div>
            <strong style="font-size: 0.8rem;">onAudioReady()</strong>
            <span style="font-size: 0.7rem; color: var(--text-muted);">High-Priority Callback</span>
          </div>
          <div class="diagram-arrow">➔</div>
          <div class="diagram-step">
            <div class="step-icon-wrap">🎛️</div>
            <strong style="font-size: 0.8rem;">C++ DSP Engine</strong>
            <span style="font-size: 0.7rem; color: var(--text-muted);">Echo & Schroeder Reverb</span>
          </div>
          <div class="diagram-arrow">➔</div>
          <div class="diagram-step">
            <div class="step-icon-wrap">💾</div>
            <strong style="font-size: 0.8rem;">WavWriter</strong>
            <span style="font-size: 0.7rem; color: var(--text-muted);">RIFF WAV Local File</span>
          </div>
        </div>
      </div>

      <div class="grid-2col">
        <!-- Left: Code Architecture in This Repo -->
        <div>
          <h3 style="font-size: 1rem; margin-bottom: 1rem; color: white;">📂 C++ Source Structure in <code style="color: #818cf8;">native-audio/</code></h3>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">1</div>
              <strong>AudioEngine.cpp / .h</strong>
            </div>
            <p style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.5rem;">
              Builds the Oboe stream, registers <code style="color: #cbd5e1;">AudioStreamCallback</code>, and feeds incoming audio chunks to active effects.
            </p>
            <div class="code-snippet">
oboe::AudioStreamBuilder builder;
builder.setDirection(oboe::AudioDirection::Input)
       ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
       ->setSharingMode(oboe::SharingMode::Exclusive)
       ->setFormat(oboe::AudioFormat::Float)
       ->setCallback(this)
       ->openStream(mStream);
            </div>
          </div>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">2</div>
              <strong>EchoEffect.cpp & ReverbEffect.cpp</strong>
            </div>
            <p style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.5rem;">
              Circular delay buffer DSP with decay scaling, and Schroeder Reverb with 8 parallel comb filters + 4 series all-pass filters.
            </p>
          </div>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">3</div>
              <strong>native-lib.cpp (JNI Bridge)</strong>
            </div>
            <p style="font-size: 0.8rem; color: var(--text-muted);">
              Exposes <code style="color: #cbd5e1;">Java_com_roxstar_audio_AudioEngine_startRecording</code> and <code style="color: #cbd5e1;">stopRecording</code> to Android Kotlin code.
            </p>
          </div>
        </div>

        <!-- Right: 5-Step Android Studio Integration Guide -->
        <div>
          <h3 style="font-size: 1rem; margin-bottom: 1rem; color: white;">🚀 5 Steps to Run on Android Studio</h3>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">1</div>
              <strong>Add Oboe Dependency in app/build.gradle</strong>
            </div>
            <div class="code-snippet">
android {
    buildFeatures { prefab true }
}
dependencies {
    implementation 'com.google.oboe:oboe:1.9.0'
}
            </div>
          </div>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">2</div>
              <strong>Link CMakeLists.txt</strong>
            </div>
            <p style="font-size: 0.775rem; color: var(--text-muted); margin-bottom: 0.5rem;">
              Already configured in <code style="color: #a5b4fc;">native-audio/CMakeLists.txt</code>!
            </p>
            <div class="code-snippet">
find_package(oboe REQUIRED CONFIG)
target_link_libraries(roxstar_audio oboe::oboe)
            </div>
          </div>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">3</div>
              <strong>Request Android Permissions</strong>
            </div>
            <div class="code-snippet">
&lt;uses-permission android:name="android.permission.RECORD_AUDIO" /&gt;
&lt;uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" /&gt;
            </div>
          </div>

          <div class="step-card">
            <div class="step-card-header">
              <div class="step-number">4</div>
              <strong>Call via Kotlin AudioEngine</strong>
            </div>
            <div class="code-snippet">
System.loadLibrary("roxstar_audio")
AudioEngine.startRecording(effect = "REVERB")
// When finished:
val filePath = AudioEngine.stopRecording()
            </div>
          </div>
        </div>
      </div>
    </div>

  </main>

  <!-- Share Draft Modal -->
  <div id="share-modal" class="modal-overlay">
    <div class="modal-box">
      <h3 style="font-size: 1.1rem; font-weight: 700; margin-bottom: 0.5rem;">Share Draft to Room</h3>
      <p style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 1.25rem;">
        Share <strong id="modal-draft-title" style="color: white;">Lead Vocal Harmony</strong> with all room participants.
      </p>

      <div style="margin-bottom: 1.25rem;">
        <label style="font-size: 0.775rem; font-weight: 600; color: var(--text-muted); display: block; margin-bottom: 0.35rem;">
          Destination Room ID
        </label>
        <input type="text" id="modal-room-id-input" class="input-field" value="room_studio_alpha" />
      </div>

      <div style="display: flex; justify-content: flex-end; gap: 0.5rem;">
        <button class="btn btn-secondary btn-sm" onclick="closeShareModal()">Cancel</button>
        <button class="btn btn-primary btn-sm" onclick="confirmShareDraft()">Share to Room</button>
      </div>
    </div>
  </div>

  <!-- Winner Banner Overlay -->
  <div id="winner-overlay" class="winner-overlay">
    <div class="winner-card">
      <div style="font-size: 3.5rem; margin-bottom: 0.5rem;">🏆</div>
      <h2 style="font-size: 1.6rem; font-weight: 800; color: white; margin-bottom: 0.5rem;">
        We Have a Winner!
      </h2>
      <div id="winner-name" style="font-size: 1.3rem; font-weight: 700; color: var(--accent-amber); margin-bottom: 1rem;">
        Alex_Vocalist
      </div>
      <p style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 1.5rem;">
        Successfully survived all 5-second elimination rounds!
      </p>
      <button class="btn btn-primary" onclick="closeWinnerModal()">
        Back to Arena
      </button>
    </div>
  </div>

  <!-- Toast -->
  <div id="toast" class="toast"></div>

  <!-- ========================================================= -->
  <!-- CLIENT JAVASCRIPT LOGIC -->
  <!-- ========================================================= -->
  <script>
    // State
    const STATE = {
      currentUser: {
        userId: 'user_alex',
        username: 'Alex_Vocalist'
      },
      currentRoomId: 'room_studio_alpha',
      roomData: null,
      myDrafts: [],
      sharedDrafts: [],
      selectedEffect: 'NONE',
      isRecording: false,
      recordingStartTime: 0,
      recordTimerInterval: null,
      audioBlobs: {}, // draftId -> Blob URL
      activeAudio: null,
      activeDraftId: null,
      draftPendingShare: null,
      // Spin Wheel State
      spinRunning: false,
      spinParticipants: [],
      spinAngles: [],
      currentWheelRotation: 0,
      spinAnimationId: null
    };

    // Socket.IO
    let socket = null;

    // Web Audio Recording
    let mediaRecorder = null;
    let audioChunks = [];
    let audioContext = null;
    let analyser = null;
    let micStream = null;

    // Toast helper
    function showToast(msg) {
      const toast = document.getElementById('toast');
      toast.textContent = msg;
      toast.style.display = 'block';
      setTimeout(() => { toast.style.display = 'none'; }, 3000);
    }

    // Switch Tabs
    function switchTab(tabId) {
      document.querySelectorAll('.nav-tab').forEach(t => {
        t.classList.toggle('active', t.getAttribute('data-tab') === tabId);
      });
      document.querySelectorAll('.tab-content').forEach(c => {
        c.classList.toggle('active', c.id === 'tab-' + tabId);
      });

      if (tabId === 'spin-wheel') {
        drawWheel();
      }
    }

    // Initialize App
    window.addEventListener('DOMContentLoaded', async () => {
      initSocket();
      initVisualizer();
      await fetchUserDrafts();
      await fetchRoomDetails(STATE.currentRoomId);
      initWheel();
    });

    // =========================================================
    // SOCKET.IO HANDLERS
    // =========================================================
    function initSocket() {
      socket = io({
        query: {
          roomId: STATE.currentRoomId,
          userId: STATE.currentUser.userId,
          username: STATE.currentUser.username
        }
      });

      socket.on('connect', () => {
        document.getElementById('ws-dot').className = 'dot dot-green';
        document.getElementById('ws-status').textContent = 'Connected';
        addActivity('Connected to real-time server');
      });

      socket.on('disconnect', () => {
        document.getElementById('ws-dot').className = 'dot dot-red';
        document.getElementById('ws-status').textContent = 'Disconnected';
        addActivity('Disconnected from server');
      });

      socket.on('room_state', (data) => {
        STATE.roomData = data;
        renderRoomUI();
      });

      socket.on('user_joined', (user) => {
        addActivity('👤 ' + user.username + ' joined the room');
        fetchRoomDetails(STATE.currentRoomId);
      });

      socket.on('user_left', (data) => {
        addActivity('👋 User ' + data.userId + ' left the room');
        fetchRoomDetails(STATE.currentRoomId);
      });

      socket.on('draft_shared', (draft) => {
        addActivity('🎵 ' + (draft.title || 'Draft') + ' shared to room');
        fetchRoomDetails(STATE.currentRoomId);
        showToast('New draft shared to room!');
      });

      // Spin events
      socket.on('spin_started', (snapshot) => {
        STATE.spinRunning = true;
        document.getElementById('spin-status-label').textContent = 'Spinning... (Round ' + snapshot.currentRound + ')';
        addActivity('🎡 Spin started! ' + snapshot.totalParticipants + ' contenders');
        if (snapshot.participants) {
          STATE.spinParticipants = snapshot.participants.map(p => ({
            userId: p.userId,
            username: p.username || p.userId,
            status: p.status || 'ACTIVE'
          }));
          drawWheel();
          renderContendersList();
        }
      });

      socket.on('user_eliminated', (data) => {
        addActivity('❌ ' + (data.eliminatedUsername || data.eliminatedUserId) + ' was eliminated!');
        animateWheelElimination(data.eliminatedUserId);
        fetchRoomDetails(STATE.currentRoomId);
      });

      socket.on('spin_ended', (data) => {
        STATE.spinRunning = false;
        document.getElementById('spin-status-label').textContent = 'Completed!';
        document.getElementById('winner-name').textContent = data.winnerUsername || data.winnerUserId;
        document.getElementById('winner-overlay').style.display = 'flex';
        addActivity('🏆 Winner crowned: ' + (data.winnerUsername || data.winnerUserId));
        fetchRoomDetails(STATE.currentRoomId);
      });

      socket.on('spin_countdown', (data) => {
        document.getElementById('spin-countdown-val').textContent = (data.remainingMs / 1000).toFixed(1) + 's';
      });

      socket.on('error_event', (err) => {
        showToast('Notice: ' + (err.message || 'Error occurred'));
      });
    }

    function addActivity(text) {
      const feed = document.getElementById('activity-feed');
      if (!feed) return;
      const now = new Date();
      const timeStr = now.toTimeString().split(' ')[0];
      const div = document.createElement('div');
      div.className = 'activity-item';
      div.innerHTML = '<span class="activity-time">' + timeStr + '</span><span>' + text + '</span>';
      feed.insertBefore(div, feed.firstChild);
      if (feed.children.length > 20) feed.removeChild(feed.lastChild);
    }

    // =========================================================
    // VOICE RECORDING & DRAFTS
    // =========================================================
    function selectEffect(effect) {
      STATE.selectedEffect = effect;
      document.querySelectorAll('.effect-chip').forEach(c => {
        c.classList.toggle('active', c.getAttribute('data-effect') === effect);
      });
    }

    async function toggleRecording() {
      if (STATE.isRecording) {
        stopRecording();
      } else {
        await startRecording();
      }
    }

    async function startRecording() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        micStream = stream;
        audioChunks = [];

        if (!audioContext) {
          audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        if (audioContext.state === 'suspended') {
          await audioContext.resume();
        }

        const source = audioContext.createMediaStreamSource(stream);
        analyser = audioContext.createAnalyser();
        analyser.fftSize = 64;
        source.connect(analyser);

        mediaRecorder = new MediaRecorder(stream);
        mediaRecorder.ondataavailable = (e) => {
          if (e.data.size > 0) audioChunks.push(e.data);
        };
        mediaRecorder.onstop = saveRecordedDraft;
        mediaRecorder.start();

        STATE.isRecording = true;
        STATE.recordingStartTime = Date.now();
        document.getElementById('btn-record-main').textContent = '⏹️ Stop & Save Draft';
        document.getElementById('btn-record-main').className = 'btn btn-primary';
        document.getElementById('btn-discard').style.display = 'inline-flex';

        STATE.recordTimerInterval = setInterval(() => {
          const elapsed = Date.now() - STATE.recordingStartTime;
          const mins = String(Math.floor(elapsed / 60000)).padStart(2, '0');
          const secs = String(Math.floor((elapsed % 60000) / 1000)).padStart(2, '0');
          const tenths = Math.floor((elapsed % 1000) / 100);
          document.getElementById('record-timer').textContent = mins + ':' + secs + '.' + tenths;
        }, 100);

      } catch (err) {
        console.warn('Microphone permission not granted, falling back to simulated recording:', err);
        startSimulatedRecording();
      }
    }

    function startSimulatedRecording() {
      STATE.isRecording = true;
      STATE.recordingStartTime = Date.now();
      document.getElementById('btn-record-main').textContent = '⏹️ Stop & Save Draft';
      document.getElementById('btn-record-main').className = 'btn btn-primary';
      document.getElementById('btn-discard').style.display = 'inline-flex';

      STATE.recordTimerInterval = setInterval(() => {
        const elapsed = Date.now() - STATE.recordingStartTime;
        const mins = String(Math.floor(elapsed / 60000)).padStart(2, '0');
        const secs = String(Math.floor((elapsed % 60000) / 1000)).padStart(2, '0');
        const tenths = Math.floor((elapsed % 1000) / 100);
        document.getElementById('record-timer').textContent = mins + ':' + secs + '.' + tenths;
      }, 100);
    }

    function stopRecording() {
      STATE.isRecording = false;
      clearInterval(STATE.recordTimerInterval);
      document.getElementById('btn-record-main').textContent = '🔴 Start Recording';
      document.getElementById('btn-record-main').className = 'btn btn-accent';
      document.getElementById('btn-discard').style.display = 'none';

      if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
      } else {
        saveRecordedDraft();
      }

      if (micStream) {
        micStream.getTracks().forEach(t => t.stop());
        micStream = null;
      }
    }

    function discardRecording() {
      STATE.isRecording = false;
      clearInterval(STATE.recordTimerInterval);
      document.getElementById('record-timer').textContent = '00:00.0';
      document.getElementById('btn-record-main').textContent = '🔴 Start Recording';
      document.getElementById('btn-record-main').className = 'btn btn-accent';
      document.getElementById('btn-discard').style.display = 'none';
      if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
      }
      audioChunks = [];
      showToast('Recording discarded');
    }

    async function saveRecordedDraft() {
      const durationMs = Math.max(Date.now() - STATE.recordingStartTime, 1500);
      document.getElementById('record-timer').textContent = '00:00.0';

      const titleInput = document.getElementById('draft-title-input');
      const title = (titleInput.value && titleInput.value.trim()) || 'Voice Take (' + STATE.selectedEffect + ')';
      titleInput.value = '';

      let blobUrl = '';
      if (audioChunks.length > 0) {
        const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
        blobUrl = URL.createObjectURL(audioBlob);
      }

      try {
        const res = await fetch('/api/drafts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userId: STATE.currentUser.userId,
            title: title,
            durationMs: durationMs,
            effectApplied: STATE.selectedEffect,
            fileUrl: blobUrl
          })
        });
        const result = await res.json();
        if (result.success) {
          const draftId = result.data.draftId;
          if (blobUrl) STATE.audioBlobs[draftId] = blobUrl;
          showToast('Draft "' + title + '" saved!');
          await fetchUserDrafts();
        }
      } catch (err) {
        console.error('Error saving draft:', err);
      }
    }

    async function fetchUserDrafts() {
      try {
        const res = await fetch('/api/drafts/user/' + STATE.currentUser.userId);
        const json = await res.json();
        if (json.success) {
          STATE.myDrafts = json.data;
          renderDraftsList();
        }
      } catch (err) {
        console.error('Failed to fetch drafts:', err);
      }
    }

    function renderDraftsList() {
      const list = document.getElementById('drafts-list');
      const countBadge = document.getElementById('drafts-count-badge');
      countBadge.textContent = STATE.myDrafts.length + ' Drafts';

      if (STATE.myDrafts.length === 0) {
        list.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 2rem 1rem; font-size: 0.85rem;">No voice drafts recorded yet.<br>Click Start Recording above!</div>';
        return;
      }

      list.innerHTML = STATE.myDrafts.map(draft => {
        const isPlaying = STATE.activeDraftId === draft.draftId;
        const durSec = (draft.durationMs / 1000).toFixed(1) + 's';
        const effectClass = draft.effectApplied === 'ECHO' ? 'draft-tag-echo' : (draft.effectApplied === 'REVERB' ? 'draft-tag-reverb' : '');

        return `
          <div class="draft-item ${isPlaying ? 'playing' : ''}" id="draft-card-${draft.draftId}">
            <div class="draft-header">
              <div class="draft-title-area" id="title-box-${draft.draftId}">
                <span class="draft-title" onclick="startInlineRename('${draft.draftId}', '${escapeHtml(draft.title)}')">
                  ${escapeHtml(draft.title)}
                </span>
                <button class="btn btn-secondary btn-icon" style="width: 22px; height: 22px; font-size: 0.7rem;" title="Rename Draft" onclick="startInlineRename('${draft.draftId}', '${escapeHtml(draft.title)}')">
                  ✏️
                </button>
              </div>

              <div class="draft-tags">
                <span class="draft-tag ${effectClass}">${draft.effectApplied || 'NONE'}</span>
                <span class="draft-tag" style="font-family: monospace;">${durSec}</span>
              </div>
            </div>

            <div class="draft-player-row">
              <button class="btn-play" onclick="togglePlayDraft('${draft.draftId}', ${draft.durationMs}, '${draft.effectApplied}')" id="btn-play-${draft.draftId}">
                ${isPlaying ? '⏸' : '▶'}
              </button>

              <div class="draft-progress-container">
                <div class="draft-progress-bar" id="progress-${draft.draftId}"></div>
              </div>

              <span class="draft-duration-text" id="time-${draft.draftId}">${durSec}</span>

              <div class="draft-actions">
                <button class="btn btn-primary btn-sm" onclick="openShareModal('${draft.draftId}', '${escapeHtml(draft.title)}')">
                  🚀 Share
                </button>
                <button class="btn btn-danger btn-icon" title="Delete Draft" onclick="deleteDraft('${draft.draftId}')">
                  🗑️
                </button>
              </div>
            </div>
          </div>
        `;
      }).join('');
    }

    // Inline Rename Draft
    function startInlineRename(draftId, currentTitle) {
      const box = document.getElementById('title-box-' + draftId);
      if (!box) return;

      box.innerHTML = `
        <input type="text" class="draft-edit-input" id="edit-input-${draftId}" value="${escapeHtml(currentTitle)}" />
        <button class="btn btn-primary btn-icon" style="width: 26px; height: 26px; font-size: 0.75rem;" onclick="saveInlineRename('${draftId}')">✓</button>
        <button class="btn btn-secondary btn-icon" style="width: 26px; height: 26px; font-size: 0.75rem;" onclick="renderDraftsList()">✕</button>
      `;

      const input = document.getElementById('edit-input-' + draftId);
      input.focus();
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') saveInlineRename(draftId);
        if (e.key === 'Escape') renderDraftsList();
      });
    }

    async function saveInlineRename(draftId) {
      const input = document.getElementById('edit-input-' + draftId);
      if (!input) return;
      const newTitle = input.value.trim();
      if (!newTitle) return renderDraftsList();

      try {
        const res = await fetch('/api/drafts/' + draftId + '/rename', {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ title: newTitle })
        });
        const result = await res.json();
        if (result.success) {
          showToast('Draft renamed to "' + newTitle + '"');
          await fetchUserDrafts();
        } else {
          showToast('Error: ' + (result.message || 'Could not rename'));
          renderDraftsList();
        }
      } catch (err) {
        showToast('Rename request failed');
        renderDraftsList();
      }
    }

    // Delete Draft
    async function deleteDraft(draftId) {
      if (!confirm('Are you sure you want to delete this voice draft?')) return;
      try {
        const res = await fetch('/api/drafts/' + draftId, { method: 'DELETE' });
        const result = await res.json();
        if (result.success) {
          showToast('Draft deleted');
          if (STATE.activeDraftId === draftId) stopPlayback();
          await fetchUserDrafts();
        }
      } catch (err) {
        showToast('Failed to delete draft');
      }
    }

    // Playback (Real Audio / Web Audio Synth)
    let playbackInterval = null;
    let playbackStart = 0;

    function togglePlayDraft(draftId, durationMs, effect) {
      if (STATE.activeDraftId === draftId) {
        stopPlayback();
      } else {
        startPlayback(draftId, durationMs, effect);
      }
    }

    function startPlayback(draftId, durationMs, effect) {
      stopPlayback();
      STATE.activeDraftId = draftId;

      const blobUrl = STATE.audioBlobs[draftId];
      if (blobUrl) {
        const audio = new Audio(blobUrl);
        STATE.activeAudio = audio;
        audio.play();
        audio.onended = () => stopPlayback();
      } else {
        // Synthesize pleasant melodious preview using Web Audio API
        playSynthesizedDraft(durationMs, effect);
      }

      playbackStart = Date.now();
      renderDraftsList();

      playbackInterval = setInterval(() => {
        const elapsed = Date.now() - playbackStart;
        const pct = Math.min((elapsed / durationMs) * 100, 100);
        const progressBar = document.getElementById('progress-' + draftId);
        if (progressBar) progressBar.style.width = pct + '%';

        if (elapsed >= durationMs) {
          stopPlayback();
        }
      }, 50);
    }

    function stopPlayback() {
      if (STATE.activeAudio) {
        STATE.activeAudio.pause();
        STATE.activeAudio = null;
      }
      clearInterval(playbackInterval);
      const oldId = STATE.activeDraftId;
      STATE.activeDraftId = null;
      if (oldId) {
        const progressBar = document.getElementById('progress-' + oldId);
        if (progressBar) progressBar.style.width = '0%';
        renderDraftsList();
      }
    }

    function playSynthesizedDraft(durationMs, effect) {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';

      const notes = [261.63, 293.66, 329.63, 392.00, 440.00, 523.25];
      let noteIndex = 0;
      osc.frequency.setValueAtTime(notes[0], ctx.currentTime);

      const noteInterval = setInterval(() => {
        noteIndex = (noteIndex + 1) % notes.length;
        if (ctx.state === 'running') {
          osc.frequency.setValueAtTime(notes[noteIndex], ctx.currentTime);
        }
      }, 350);

      gain.gain.setValueAtTime(0.2, ctx.currentTime);

      if (effect === 'ECHO') {
        const delay = ctx.createDelay();
        delay.delayTime.value = 0.25;
        const feedback = ctx.createGain();
        feedback.gain.value = 0.45;
        delay.connect(feedback);
        feedback.connect(delay);
        osc.connect(delay);
        delay.connect(ctx.destination);
      } else if (effect === 'REVERB') {
        const filter = ctx.createBiquadFilter();
        filter.type = 'lowpass';
        filter.frequency.value = 1800;
        osc.connect(filter);
        filter.connect(ctx.destination);
      }

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();

      setTimeout(() => {
        clearInterval(noteInterval);
        try {
          gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.1);
          setTimeout(() => osc.stop(), 150);
        } catch (e) {}
      }, durationMs);
    }

    // =========================================================
    // ROOM SECTION LOGIC
    // =========================================================
    async function fetchRoomDetails(roomId) {
      try {
        const res = await fetch('/api/rooms/' + roomId);
        const json = await res.json();
        if (json.success) {
          STATE.roomData = json.data;
          renderRoomUI();
        }
      } catch (err) {
        console.error('Error fetching room details:', err);
      }
    }

    function renderRoomUI() {
      if (!STATE.roomData) return;
      const room = STATE.roomData;

      document.getElementById('room-display-name').textContent = room.name || room.id;
      document.getElementById('room-code-text').textContent = room.id;
      document.getElementById('room-status-badge').textContent = room.status || 'IDLE';
      document.getElementById('room-host-badge').textContent = room.ownerId || 'Unknown';

      const participants = room.participants || [];
      document.getElementById('member-count-badge').textContent = participants.length + ' / ' + (room.maxParticipants || 20);

      const membersGrid = document.getElementById('room-members-grid');
      membersGrid.innerHTML = participants.map(m => {
        const isMe = m.userId === STATE.currentUser.userId;
        const initial = (m.username || m.userId || 'U').charAt(0).toUpperCase();
        const isConnected = m.connectionStatus === 'CONNECTED';

        return `
          <div class="member-chip">
            <div class="member-avatar">${initial}</div>
            <div class="member-info">
              <div class="member-name">
                ${escapeHtml(m.username || m.userId)} ${isMe ? '<span style="color: var(--accent-pink); font-size: 0.7rem;">(You)</span>' : ''}
              </div>
              <div class="member-role">
                <span class="dot ${isConnected ? 'dot-green' : 'dot-amber'}" style="display: inline-block; vertical-align: middle; margin-right: 3px;"></span>
                ${m.role || 'MEMBER'}
              </div>
            </div>
          </div>
        `;
      }).join('');

      // Shared Drafts
      const sharedList = document.getElementById('shared-drafts-list');
      const sharedDrafts = room.sharedDrafts || [];
      document.getElementById('shared-drafts-badge').textContent = sharedDrafts.length + ' Shared';

      if (sharedDrafts.length === 0) {
        sharedList.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 1.5rem; font-size: 0.825rem;">No drafts shared to this room yet. Share one from Voice Studio!</div>';
      } else {
        sharedList.innerHTML = sharedDrafts.map(d => {
          const isPlaying = STATE.activeDraftId === d.id;
          const durSec = (d.durationMs / 1000).toFixed(1) + 's';
          return `
            <div class="draft-item ${isPlaying ? 'playing' : ''}">
              <div class="draft-header">
                <div class="draft-title-area">
                  <span class="draft-title">${escapeHtml(d.title)}</span>
                </div>
                <div class="draft-tags">
                  <span class="draft-tag">${d.effectApplied || 'NONE'}</span>
                  <span class="draft-tag" style="color: #cbd5e1;">By ${escapeHtml(d.userId)}</span>
                </div>
              </div>
              <div class="draft-player-row">
                <button class="btn-play" onclick="togglePlayDraft('${d.id}', ${d.durationMs}, '${d.effectApplied}')">
                  ${isPlaying ? '⏸' : '▶'}
                </button>
                <div class="draft-progress-container">
                  <div class="draft-progress-bar" id="progress-${d.id}"></div>
                </div>
                <span class="draft-duration-text">${durSec}</span>
              </div>
            </div>
          `;
        }).join('');
      }

      // Update Spin Wheel participants
      STATE.spinParticipants = participants.map(p => ({
        userId: p.userId,
        username: p.username || p.userId,
        status: p.connectionStatus
      }));
      drawWheel();
      renderContendersList();
    }

    function copyRoomCode() {
      const code = document.getElementById('room-code-text').textContent;
      navigator.clipboard.writeText(code);
      showToast('Room code copied: ' + code);
    }

    async function joinRoomFromInput() {
      const input = document.getElementById('join-room-input');
      const roomId = input.value.trim();
      if (!roomId) return;

      try {
        const res = await fetch('/api/rooms/' + roomId + '/join', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userId: STATE.currentUser.userId,
            username: STATE.currentUser.username
          })
        });
        const json = await res.json();
        if (json.success) {
          STATE.currentRoomId = roomId;
          socket.emit('join_room', {
            roomId: roomId,
            userId: STATE.currentUser.userId,
            username: STATE.currentUser.username
          });
          showToast('Joined room ' + roomId);
          input.value = '';
          await fetchRoomDetails(roomId);
        } else {
          showToast('Could not join: ' + (json.message || 'Error'));
        }
      } catch (err) {
        showToast('Join request failed');
      }
    }

    async function createNewRoom() {
      const input = document.getElementById('create-room-name-input');
      const name = input.value.trim();
      if (!name) return;

      try {
        const res = await fetch('/api/rooms', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: name,
            ownerId: STATE.currentUser.userId,
            maxParticipants: 20
          })
        });
        const json = await res.json();
        if (json.success) {
          const newRoomId = json.data.id;
          STATE.currentRoomId = newRoomId;
          socket.emit('join_room', {
            roomId: newRoomId,
            userId: STATE.currentUser.userId,
            username: STATE.currentUser.username
          });
          showToast('Room created: ' + name);
          input.value = '';
          await fetchRoomDetails(newRoomId);
        } else {
          showToast('Error creating room');
        }
      } catch (err) {
        showToast('Room creation failed');
      }
    }

    async function leaveCurrentRoom() {
      try {
        await fetch('/api/rooms/' + STATE.currentRoomId + '/leave', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId: STATE.currentUser.userId })
        });
        socket.emit('leave_room', {
          roomId: STATE.currentRoomId,
          userId: STATE.currentUser.userId
        });
        showToast('Left room');
        STATE.currentRoomId = 'room_studio_alpha';
        socket.emit('join_room', {
          roomId: 'room_studio_alpha',
          userId: STATE.currentUser.userId,
          username: STATE.currentUser.username
        });
        await fetchRoomDetails('room_studio_alpha');
      } catch (err) {
        showToast('Error leaving room');
      }
    }

    // Share Modal
    function openShareModal(draftId, title) {
      STATE.draftPendingShare = draftId;
      document.getElementById('modal-draft-title').textContent = title;
      document.getElementById('modal-room-id-input').value = STATE.currentRoomId;
      document.getElementById('share-modal').style.display = 'flex';
    }

    function closeShareModal() {
      document.getElementById('share-modal').style.display = 'none';
      STATE.draftPendingShare = null;
    }

    async function confirmShareDraft() {
      const draftId = STATE.draftPendingShare;
      const targetRoomId = document.getElementById('modal-room-id-input').value.trim() || STATE.currentRoomId;
      if (!draftId) return closeShareModal();

      const draft = STATE.myDrafts.find(d => d.draftId === draftId);
      if (!draft) return closeShareModal();

      try {
        const res = await fetch('/api/rooms/' + targetRoomId + '/drafts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userId: STATE.currentUser.userId,
            draftId: draftId,
            title: draft.title,
            durationMs: draft.durationMs,
            effectApplied: draft.effectApplied,
            fileUrl: draft.fileUrl || ''
          })
        });
        const json = await res.json();
        if (json.success) {
          socket.emit('share_draft', {
            roomId: targetRoomId,
            draftId: draftId,
            title: draft.title,
            durationMs: draft.durationMs,
            effectApplied: draft.effectApplied,
            fileUrl: draft.fileUrl || ''
          });
          showToast('Draft shared to room!');
          closeShareModal();
          await fetchRoomDetails(STATE.currentRoomId);
        } else {
          showToast('Share error: ' + (json.message || 'Failed'));
        }
      } catch (err) {
        showToast('Failed to share draft');
        closeShareModal();
      }
    }

    // =========================================================
    // SPIN WHEEL SYSTEM (CANVAS & 5-SEC ELIMINATION LOOP)
    // =========================================================
    const WHEEL_COLORS = [
      '#6366f1', '#ec4899', '#06b6d4', '#f59e0b',
      '#10b981', '#8b5cf6', '#f43f5e', '#3b82f6'
    ];

    function initWheel() {
      drawWheel();
    }

    function drawWheel() {
      const canvas = document.getElementById('wheel-canvas');
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      const centerX = canvas.width / 2;
      const centerY = canvas.height / 2;
      const radius = canvas.width / 2 - 10;

      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const contenders = STATE.spinParticipants || [];
      if (contenders.length === 0) {
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, 0, 2 * Math.PI);
        ctx.fillStyle = '#1e293b';
        ctx.fill();
        ctx.strokeStyle = 'rgba(255,255,255,0.1)';
        ctx.stroke();

        ctx.fillStyle = '#94a3b8';
        ctx.font = 'bold 14px "Plus Jakarta Sans"';
        ctx.textAlign = 'center';
        ctx.fillText('No Contenders Joined', centerX, centerY);
        return;
      }

      const numSlices = contenders.length;
      const arc = (2 * Math.PI) / numSlices;

      ctx.save();
      ctx.translate(centerX, centerY);
      ctx.rotate(STATE.currentWheelRotation);

      for (let i = 0; i < numSlices; i++) {
        const startAngle = i * arc;
        const endAngle = startAngle + arc;

        ctx.beginPath();
        ctx.moveTo(0, 0);
        ctx.arc(0, 0, radius, startAngle, endAngle);
        ctx.closePath();

        ctx.fillStyle = WHEEL_COLORS[i % WHEEL_COLORS.length];
        ctx.fill();
        ctx.lineWidth = 2;
        ctx.strokeStyle = '#07090f';
        ctx.stroke();

        ctx.save();
        ctx.rotate(startAngle + arc / 2);
        ctx.textAlign = 'right';
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 13px "Plus Jakarta Sans"';
        ctx.shadowColor = 'rgba(0,0,0,0.6)';
        ctx.shadowBlur = 4;
        const name = contenders[i].username || contenders[i].userId;
        const truncated = name.length > 12 ? name.substring(0, 10) + '..' : name;
        ctx.fillText(truncated, radius - 25, 5);
        ctx.restore();
      }

      ctx.restore();
    }

    function renderContendersList() {
      const list = document.getElementById('spin-participants-list');
      const badge = document.getElementById('contenders-badge');
      const contenders = STATE.spinParticipants || [];
      badge.textContent = contenders.length + ' Contenders';

      list.innerHTML = contenders.map((c, i) => {
        const color = WHEEL_COLORS[i % WHEEL_COLORS.length];
        return `
          <div style="display: flex; align-items: center; justify-content: space-between; padding: 0.65rem 0.85rem; background: var(--bg-surface); border-radius: 8px; border-left: 4px solid ${color};">
            <span style="font-weight: 600; font-size: 0.85rem;">${escapeHtml(c.username || c.userId)}</span>
            <span class="badge" style="background: rgba(255,255,255,0.06); color: #cbd5e1;">Active</span>
          </div>
        `;
      }).join('');
    }

    async function startSpin() {
      try {
        const res = await fetch('/api/rooms/' + STATE.currentRoomId + '/spin/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ initiatedBy: STATE.currentUser.userId })
        });
        const json = await res.json();
        if (json.success) {
          showToast('Spin started!');
        } else {
          showToast('Spin error: ' + (json.message || 'Cannot start spin'));
        }
      } catch (err) {
        showToast('Start spin failed');
      }
    }

    function animateWheelElimination(eliminatedUserId) {
      let speed = 0.25;
      const friction = 0.985;

      function spinAnim() {
        STATE.currentWheelRotation += speed;
        speed *= friction;
        drawWheel();

        if (speed > 0.005) {
          STATE.spinAnimationId = requestAnimationFrame(spinAnim);
        } else {
          showToast('Round ended: Player eliminated!');
        }
      }

      cancelAnimationFrame(STATE.spinAnimationId);
      spinAnim();
    }

    function closeWinnerModal() {
      document.getElementById('winner-overlay').style.display = 'none';
    }

    async function addDemoParticipants() {
      const demoUsers = [
        { id: 'user_sarah', name: 'Sarah_SoundEng' },
        { id: 'user_mike', name: 'Mike_Producer' },
        { id: 'user_elena', name: 'Elena_Singer' }
      ];

      for (const u of demoUsers) {
        try {
          await fetch('/api/rooms/' + STATE.currentRoomId + '/join', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId: u.id, username: u.name })
          });
        } catch (e) {}
      }

      showToast('Added demo participants to arena!');
      await fetchRoomDetails(STATE.currentRoomId);
    }

    // Waveform visualizer loop
    function initVisualizer() {
      const canvas = document.getElementById('recorder-canvas');
      if (!canvas) return;
      const ctx = canvas.getContext('2d');

      function renderVisualizer() {
        requestAnimationFrame(renderVisualizer);
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const width = canvas.width;
        const height = canvas.height;
        const centerY = height / 2;

        if (STATE.isRecording && analyser) {
          const bufferLength = analyser.frequencyBinCount;
          const dataArray = new Uint8Array(bufferLength);
          analyser.getByteTimeDomainData(dataArray);

          ctx.lineWidth = 2;
          ctx.strokeStyle = '#6366f1';
          ctx.beginPath();

          const sliceWidth = width / bufferLength;
          let x = 0;

          for (let i = 0; i < bufferLength; i++) {
            const v = dataArray[i] / 128.0;
            const y = v * (height / 2);

            if (i === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);

            x += sliceWidth;
          }

          ctx.lineTo(width, centerY);
          ctx.stroke();
        } else {
          // Idle ambient pulse
          ctx.lineWidth = 1.5;
          ctx.strokeStyle = 'rgba(99, 102, 241, 0.25)';
          ctx.beginPath();
          const t = Date.now() / 400;
          for (let x = 0; x < width; x += 5) {
            const y = centerY + Math.sin(x * 0.05 + t) * 6;
            if (x === 0) ctx.moveTo(x, y);
            else ctx.lineTo(x, y);
          }
          ctx.stroke();
        }
      }

      renderVisualizer();
    }

    function escapeHtml(str) {
      if (!str) return '';
      return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }
  </script>
</body>
</html>
'''

target_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '../public/index.html'))
with open(target_path, 'w', encoding='utf-8') as f:
    f.write(html_content)

print(f"Successfully generated {target_path} (size: {os.path.getsize(target_path)} bytes)")
