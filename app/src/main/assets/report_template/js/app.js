const AppState = {
    config: {},
    dashboard: { stats: [], aiSummaries: [] },
    members: [],
    messages: []
};

async function loadData() {
    if (window.EXPORT_DATA) {
        Object.assign(AppState, window.EXPORT_DATA);
        processAnalytics();
        return;
    }
    try {
        const [config, dashboard, members, messages] = await Promise.all([
            fetch('./data/config.json').then(r => r.json()).catch(() => ({})),
            fetch('./data/dashboard.json').then(r => r.json()).catch(() => ({ stats: [], aiSummaries: [] })),
            fetch('./data/members.json').then(r => r.json()).catch(() => []),
            fetch('./data/messages.json').then(r => r.json()).catch(() => [])
        ]);
        AppState.config = config;
        AppState.dashboard = dashboard;
        AppState.members = members;
        AppState.messages = messages;
        processAnalytics();
    } catch (e) {
        console.error("Error loading data", e);
    }
}

function processAnalytics() {
    const msgs = AppState.messages || [];
    const members = AppState.members || [];
    
    // Sort messages by time
    msgs.sort((a,b) => (a.timestamp || parseInt(a.timestamp)) - (b.timestamp || parseInt(b.timestamp)));
    
    let firstDate = msgs.length > 0 ? new Date(msgs[0].timestamp || parseInt(msgs[0].timestamp)) : new Date();
    let lastDate = msgs.length > 0 ? new Date(msgs[msgs.length-1].timestamp || parseInt(msgs[msgs.length-1].timestamp)) : new Date();
    
    let daysDiff = Math.max(1, Math.ceil((lastDate - firstDate) / (1000 * 60 * 60 * 24)));
    
    let activeDaysSet = new Set();
    let hourlyCounts = new Array(24).fill(0);
    let dailyCounts = {}; // YYYY-MM-DD to count
    let totalWords = 0;
    
    msgs.forEach(m => {
        let d = new Date(m.timestamp || parseInt(m.timestamp));
        let dateStr = d.toISOString().split('T')[0];
        activeDaysSet.add(dateStr);
        hourlyCounts[d.getHours()]++;
        dailyCounts[dateStr] = (dailyCounts[dateStr] || 0) + 1;
        
        let text = m.messageText || m.text || '';
        totalWords += text.split(/\s+/).filter(w => w.length > 0).length;
    });

    let totalMembers = members.length;
    let foundMembers = members.filter(m => (m.messageCount || 0) > 0).length;
    let silentMembers = members.filter(m => (m.messageCount || 0) === 0).length;
    let totalMedia = msgs.filter(m => m.isMedia || (m.messageText||m.text||'').includes('<Media omitted>')).length;
    
    let avgMsgsPerDay = msgs.length > 0 ? Math.round(msgs.length / daysDiff) : 0;
    let activityScore = Math.min(100, Math.round((foundMembers / Math.max(1, totalMembers)) * 50 + (avgMsgsPerDay / 50) * 50));

    AppState.analytics = {
        dateRange: `${firstDate.toLocaleDateString()} - ${lastDate.toLocaleDateString()}`,
        activeDays: activeDaysSet.size,
        avgMsgsPerDay,
        activityScore,
        totalMembers,
        foundMembers,
        silentMembers,
        totalMessages: msgs.length,
        totalMedia,
        hourlyCounts,
        dailyCounts,
        totalWords,
        avgWordsPerMsg: msgs.length > 0 ? (totalWords / msgs.length).toFixed(1) : 0
    };
}

function renderDashboard() {
    const container = document.getElementById('page-container');
    const a = AppState.analytics;
    const members = [...AppState.members];
    
    members.sort((a,b) => (b.messageCount||0) - (a.messageCount||0));
    const topContributors = members.slice(0, 3);
    const leastContributors = members.filter(m => (m.messageCount||0) > 0).reverse().slice(0, 3);

    // Heatmap HTML
    let heatmapHtml = '<div class="heatmap-grid">';
    heatmapHtml += '<div></div>'; // empty top-left
    for(let h=0; h<24; h++) heatmapHtml += `<div class="heatmap-header">${h}</div>`;
    
    const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    let dayHourMap = Array(7).fill(0).map(() => Array(24).fill(0));
    let maxCell = 1;
    
    AppState.messages.forEach(m => {
        let d = new Date(m.timestamp || parseInt(m.timestamp));
        dayHourMap[d.getDay()][d.getHours()]++;
        if (dayHourMap[d.getDay()][d.getHours()] > maxCell) maxCell = dayHourMap[d.getDay()][d.getHours()];
    });

    for(let d=0; d<7; d++) {
        heatmapHtml += `<div class="heatmap-label">${daysOfWeek[d]}</div>`;
        for(let h=0; h<24; h++) {
            let val = dayHourMap[d][h];
            let intensity = val === 0 ? 0 : Math.max(0.1, val / maxCell);
            heatmapHtml += `<div class="heatmap-cell" style="background-color: rgba(15, 118, 110, ${intensity})" title="${daysOfWeek[d]} ${h}:00 - ${val} msgs"></div>`;
        }
    }
    heatmapHtml += '</div>';

    // Daily Chart HTML (last 30 days roughly)
    let sortedDays = Object.keys(a.dailyCounts).sort();
    let recentDays = sortedDays.slice(-30);
    let maxDaily = Math.max(1, ...recentDays.map(d => a.dailyCounts[d]));
    let chartHtml = '<div class="chart-container">';
    recentDays.forEach(d => {
        let val = a.dailyCounts[d];
        let pct = (val / maxDaily) * 100;
        chartHtml += `<div class="chart-bar" style="height: ${pct}%;" title="${d}: ${val} msgs"></div>`;
    });
    chartHtml += '</div>';

    container.innerHTML = `
        <div class="page-header">
            <div>
                <h1 class="page-title">Dashboard</h1>
                <p class="page-subtitle">WhatsApp Analytics Overview</p>
            </div>
            <button class="btn btn-outline" onclick="window.location.hash='reports'"><span class="material-icons-outlined">print</span> Generate Report</button>
        </div>
        
        <div class="grid-4" style="margin-bottom: 2rem;">
            <div class="card">
                <div class="stat-label">Date Range</div>
                <div class="stat-value" style="font-size: 1.25rem; margin-top: 1rem;">${a.dateRange}</div>
            </div>
            <div class="card">
                <div class="stat-label">Active Days</div>
                <div class="stat-value">${a.activeDays}</div>
            </div>
            <div class="card">
                <div class="stat-label">Avg Msgs / Day</div>
                <div class="stat-value">${a.avgMsgsPerDay}</div>
            </div>
            <div class="card">
                <div class="stat-label">Activity Score</div>
                <div class="stat-value" style="color: var(--success);">${a.activityScore}/100</div>
            </div>
        </div>

        <div class="grid-4" style="margin-bottom: 2rem;">
            <div class="card">
                <div class="stat-label">Total Group Members</div>
                <div class="stat-value">${a.totalMembers}</div>
            </div>
            <div class="card">
                <div class="stat-label">Participants Found</div>
                <div class="stat-value">${a.foundMembers}</div>
            </div>
            <div class="card">
                <div class="stat-label">Silent Members</div>
                <div class="stat-value" style="color: var(--danger);">${a.silentMembers}</div>
            </div>
            <div class="card">
                <div class="stat-label">Total Messages</div>
                <div class="stat-value">${a.totalMessages}</div>
            </div>
        </div>

        <div class="grid-2" style="margin-bottom: 2rem;">
            <div class="card">
                <h2 class="card-title">Activity Overview (Last 30 Active Days)</h2>
                ${chartHtml}
            </div>
            <div class="card" style="overflow-x: auto;">
                <h2 class="card-title">Activity Heatmap (Hourly)</h2>
                ${heatmapHtml}
            </div>
        </div>

        <div class="grid-2">
            <div class="card">
                <h2 class="card-title"><span class="material-icons-outlined">trending_up</span> Top Contributors</h2>
                <div>
                    ${topContributors.map(m => `
                        <div class="member-card clickable" onclick="window.location.hash='profile-${m.id}'">
                            <div class="avatar">${m.name.substring(0,2).toUpperCase()}</div>
                            <div class="member-details">
                                <div class="member-name">${m.name}</div>
                                <div class="member-meta">${m.wordCount || 0} words • ${m.mediaCount || 0} media</div>
                            </div>
                            <div class="member-score">${m.messageCount || 0}</div>
                        </div>
                    `).join('')}
                </div>
            </div>
            <div class="card">
                <h2 class="card-title"><span class="material-icons-outlined">trending_down</span> Least Contributors (Active)</h2>
                <div>
                    ${leastContributors.map(m => `
                        <div class="member-card clickable" onclick="window.location.hash='profile-${m.id}'">
                            <div class="avatar">${m.name.substring(0,2).toUpperCase()}</div>
                            <div class="member-details">
                                <div class="member-name">${m.name}</div>
                                <div class="member-meta">${m.wordCount || 0} words</div>
                            </div>
                            <div class="member-score">${m.messageCount || 0}</div>
                        </div>
                    `).join('')}
                </div>
            </div>
        </div>
    `;
}

function renderMembers() {
    const container = document.getElementById('page-container');
    const members = AppState.members;
    const a = AppState.analytics;
    
    container.innerHTML = `
        <div class="page-header">
            <div>
                <h1 class="page-title">Members</h1>
                <p class="page-subtitle">Total: ${a.totalMembers} | Found: ${a.foundMembers} | Silent: ${a.silentMembers}</p>
            </div>
        </div>
        <div class="filter-bar">
            <span class="material-icons-outlined">filter_list</span>
            <select id="member-sort" class="select-input">
                <option value="name">Alphabetical</option>
                <option value="msg-desc">Top Contributors</option>
                <option value="msg-asc">Least Contributors</option>
                <option value="words">Most Words</option>
                <option value="media">Most Media</option>
                <option value="images">Most Images</option>
                <option value="silent">Silent Members</option>
            </select>
            <input type="text" id="member-search" class="text-input" placeholder="Search members by name or phone..." style="flex: 1;" />
        </div>
        <div class="table-wrapper">
            <table>
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Phone</th>
                        <th>Messages</th>
                        <th>Words</th>
                        <th>Media</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody id="member-tbody">
                </tbody>
            </table>
        </div>
    `;

    const renderTable = (data) => {
        const tbody = document.getElementById('member-tbody');
        tbody.innerHTML = data.map(m => {
            const isSilent = (m.messageCount || 0) === 0;
            const statusBadge = isSilent 
                ? '<span class="badge badge-danger">No messages found</span>' 
                : '<span class="badge badge-success">Active</span>';
            return `
            <tr class="clickable" onclick="window.location.hash = 'profile-${m.id}'">
                <td style="font-weight: 600; color: var(--text-main);">
                    <div style="display:flex; align-items:center; gap: 10px;">
                        <div class="avatar" style="width:32px; height:32px; font-size:0.8rem;">${m.name.substring(0,2).toUpperCase()}</div>
                        ${m.name}
                    </div>
                </td>
                <td style="color: var(--text-muted);">${m.normalizedPhone || m.phone || '-'}</td>
                <td style="font-weight: 600;">${m.messageCount || 0}</td>
                <td>${m.wordCount || 0}</td>
                <td>${m.mediaCount || 0}</td>
                <td>${statusBadge}</td>
            </tr>
        `}).join('');
    };

    renderTable(members);

    const filterSort = () => {
        const term = document.getElementById('member-search').value.toLowerCase();
        const val = document.getElementById('member-sort').value;
        
        let filtered = members.filter(m => m.name.toLowerCase().includes(term) || ((m.phone||'').includes(term)) || ((m.normalizedPhone||'').includes(term)));
        
        if (val === 'silent') filtered = filtered.filter(m => (m.messageCount||0) === 0);
        else if (val === 'msg-asc') filtered = filtered.filter(m => (m.messageCount||0) > 0);
        
        if (val === 'name') filtered.sort((a,b) => a.name.localeCompare(b.name));
        if (val === 'msg-desc') filtered.sort((a,b) => (b.messageCount||0) - (a.messageCount||0));
        if (val === 'msg-asc') filtered.sort((a,b) => (a.messageCount||0) - (b.messageCount||0));
        if (val === 'words') filtered.sort((a,b) => (b.wordCount||0) - (a.wordCount||0));
        if (val === 'media') filtered.sort((a,b) => (b.mediaCount||0) - (a.mediaCount||0));
        if (val === 'images') filtered.sort((a,b) => (b.imagesShared||0) - (a.imagesShared||0));
        
        renderTable(filtered);
    };

    document.getElementById('member-search').addEventListener('input', filterSort);
    document.getElementById('member-sort').addEventListener('change', filterSort);
}

function renderProfile(id) {
    const container = document.getElementById('page-container');
    const member = AppState.members.find(m => m.id == id) || AppState.members[0];
    if (!member) {
        container.innerHTML = '<p>Member not found.</p>';
        return;
    }

    const initials = member.name.substring(0,2).toUpperCase();
    const isSilent = (member.messageCount || 0) === 0;
    const statusText = isSilent ? "No messages found in this exported chat" : "Active Participant";

    container.innerHTML = `
        <button class="btn btn-outline" onclick="window.history.back()" style="margin-bottom: 1rem;"><span class="material-icons-outlined">arrow_back</span> Back</button>
        <div class="profile-header">
            <div class="profile-avatar">${initials}</div>
            <div class="profile-info">
                <h2>${member.name}</h2>
                <p><span class="material-icons-outlined">phone</span> ${member.normalizedPhone || member.phone || 'No phone number'}</p>
                <div style="margin-top: 0.75rem;"><span class="badge ${isSilent ? 'badge-danger' : 'badge-success'}">${statusText}</span></div>
            </div>
        </div>
        <div class="grid-4" style="margin-bottom: 2rem;">
            <div class="card">
                <div class="stat-label">Messages</div>
                <div class="stat-value">${member.messageCount || 0}</div>
            </div>
            <div class="card">
                <div class="stat-label">Words</div>
                <div class="stat-value">${member.wordCount || 0}</div>
            </div>
            <div class="card">
                <div class="stat-label">Characters</div>
                <div class="stat-value">${member.characterCount || 0}</div>
            </div>
            <div class="card">
                <div class="stat-label">Media Shared</div>
                <div class="stat-value">${member.mediaCount || 0}</div>
            </div>
        </div>
        <div class="card">
            <h2 class="card-title"><span class="material-icons-outlined">pie_chart</span> Media Breakdown</h2>
            <div class="grid-4" style="margin-top: 1.5rem;">
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Images</strong> <span>${member.imagesShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Videos</strong> <span>${member.videosShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Audio</strong> <span>${member.audioShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Voice Notes</strong> <span>${member.voiceNotesShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Documents</strong> <span>${member.documentsShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>GIFs</strong> <span>${member.gifsShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Stickers</strong> <span>${member.stickersShared || 0}</span></div>
                <div style="display:flex; justify-content:space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;"><strong>Locations</strong> <span>${member.locationsShared || 0}</span></div>
            </div>
        </div>
    `;
}

function renderMessages() {
    const container = document.getElementById('page-container');
    const messages = AppState.messages;
    
    container.innerHTML = `
        <div class="page-header">
            <div>
                <h1 class="page-title">Messages</h1>
                <p class="page-subtitle">Total: ${messages.length} messages available in export</p>
            </div>
        </div>
        <div class="filter-bar">
            <span class="material-icons-outlined">search</span>
            <input type="text" id="msg-search" class="text-input" placeholder="Search message text, sender..." style="flex: 1;" />
        </div>
        <div id="messages-list"></div>
    `;

    const listContainer = document.getElementById('messages-list');
    
    const renderList = (data) => {
        const toShow = data.slice(0, 500); 
        let html = toShow.map((msg, index) => {
            const date = new Date(msg.timestamp || parseInt(msg.timestamp)).toLocaleString();
            return `
            <div class="message-bubble">
                <div class="msg-header">
                    <div class="msg-sender"><div class="avatar" style="width:24px; height:24px; font-size:0.6rem;">${(msg.senderName||msg.sender||'U').substring(0,2).toUpperCase()}</div> ${msg.senderName || msg.sender}</div>
                    <div class="msg-time">${date}</div>
                </div>
                ${msg.isReply ? `<div class="msg-reply">Reply to previous message</div>` : ''}
                <div class="msg-text">${msg.messageText || msg.text || ''}</div>
                ${msg.isMedia ? `<div class="msg-meta"><span class="material-icons-outlined">attachment</span> ${msg.messageType || 'Media Attachment'}</div>` : ''}
            </div>
        `}).join('');
        if (data.length > 500) {
            html += `<div class="card" style="text-align: center; color: var(--text-muted);">Showing first 500 of " + data.length + " messages. Use search to find specific content.</div>`;
        } else if (data.length === 0) {
            html += `<div class="card" style="text-align: center; color: var(--text-muted);">No messages found matching your search.</div>`;
        }
        listContainer.innerHTML = html;
    };

    renderList(messages);

    document.getElementById('msg-search').addEventListener('input', (e) => {
        const term = e.target.value.toLowerCase();
        if (!term) return renderList(messages);
        
        const filtered = messages.filter(m => 
            (m.messageText || m.text || '').toLowerCase().includes(term) || 
            (m.senderName || m.sender || '').toLowerCase().includes(term)
        );
        renderList(filtered);
    });
}

function renderGallery() {
    const container = document.getElementById('page-container');
    const mediaFiles = AppState.media || [];
    
    // Group media by type
    const mediaByType = mediaFiles.reduce((acc, m) => {
        acc[m.fileType] = acc[m.fileType] || [];
        acc[m.fileType].push(m);
        return acc;
    }, {});
    
    let html = `
        <div class="page-header">
            <div>
                <h1 class="page-title">Media & Files</h1>
                <p class="page-subtitle">${mediaFiles.length} files found in export</p>
            </div>
        </div>
        
        <div style="display: flex; gap: 1rem; margin-bottom: 2rem; overflow-x: auto; padding-bottom: 0.5rem;">
            <button class="btn btn-primary" onclick="filterGallery('ALL')">All</button>
            <button class="btn btn-secondary" onclick="filterGallery('IMAGE')">Images</button>
            <button class="btn btn-secondary" onclick="filterGallery('VIDEO')">Videos</button>
            <button class="btn btn-secondary" onclick="filterGallery('AUDIO')">Audio</button>
            <button class="btn btn-secondary" onclick="filterGallery('DOCUMENT')">Documents</button>
        </div>
        
        <div class="media-grid" id="media-grid"></div>
    `;
    
    container.innerHTML = html;
    
    window.currentMedia = mediaFiles;
    renderMediaGrid(mediaFiles);
}

window.filterGallery = function(type) {
    const mediaFiles = AppState.media || [];
    if (type === 'ALL') {
        window.currentMedia = mediaFiles;
    } else {
        window.currentMedia = mediaFiles.filter(m => m.fileType === type || (type==='AUDIO' && m.fileType==='VOICE'));
    }
    renderMediaGrid(window.currentMedia);
};

window.renderMediaGrid = function(files) {
    const grid = document.getElementById('media-grid');
    if (!files || files.length === 0) {
        grid.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; padding: 2rem; color: var(--text-muted);">No media found.</div>';
        return;
    }
    
    grid.innerHTML = files.slice(0, 500).map(m => {
        const type = m.fileType || 'DOCUMENT';
        let contentHtml = '';
        
        // Define directory based on type logic in Kotlin
        let dir = 'documents';
        if (type === 'IMAGE') dir = 'images';
        else if (type === 'VIDEO') dir = 'videos';
        else if (type === 'AUDIO' || type === 'VOICE') dir = 'audio';
        
        const filePath = `${dir}/${m.fileName}`;
        
        if (type === 'IMAGE') {
            contentHtml = `<img src="${filePath}" onerror="this.outerHTML='<div class=\'media-missing\'>Missing</div>'" style="width:100%; height:100%; object-fit:cover;">`;
        } else if (type === 'VIDEO') {
            contentHtml = `<video src="${filePath}" onerror="this.outerHTML='<div class=\'media-missing\'>Missing</div>'" controls style="width:100%; height:100%; object-fit:cover;"></video>`;
        } else if (type === 'AUDIO' || type === 'VOICE') {
            contentHtml = `<div style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; padding:1rem; background:var(--surface);">
                <span class="material-icons-outlined" style="font-size:2rem; margin-bottom:0.5rem">audiotrack</span>
                <audio src="${filePath}" onerror="this.parentElement.innerHTML='<div class=\'media-missing\'>Missing</div>'" controls style="width:100%; height:32px;"></audio>
            </div>`;
        } else {
            contentHtml = `<div style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; background:var(--surface);">
                <span class="material-icons-outlined" style="font-size:3rem;">insert_drive_file</span>
                <span style="margin-top:0.5rem; text-align:center; font-size:0.8rem; word-break:break-all; padding: 0 0.5rem;">${m.fileName}</span>
                <a href="${filePath}" target="_blank" onerror="this.outerHTML='<div class=\'media-missing\'>Missing</div>'" style="margin-top:0.5rem; font-size:0.8rem;">Open File</a>
            </div>`;
        }
        
        return `
        <div class="media-item" style="position:relative; border: 1px solid var(--border); border-radius:8px; overflow:hidden; display:flex; flex-direction:column;" title="From ${m.senderName} on ${new Date(m.timestamp).toLocaleString()}">
            <div style="flex:1; overflow:hidden; position:relative; min-height: 150px;">
                ${contentHtml}
            </div>
            <div style="padding:0.5rem; background: rgba(0,0,0,0.7); color:white; font-size:0.75rem; position:absolute; bottom:0; left:0; right:0;">
                <div style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${m.senderName}</div>
                <div style="opacity:0.8;">${new Date(m.timestamp).toLocaleDateString()}</div>
            </div>
        </div>
    `}).join('');
    
    if (files.length > 500) {
        grid.innerHTML += `<div style="grid-column: 1 / -1; text-align: center; padding: 2rem; color: var(--text-muted);">Showing first 500 media items.</div>`;
    }
};

function renderAnalytics() {
    const container = document.getElementById('page-container');
    const a = AppState.analytics;
    
    let maxHourly = Math.max(...a.hourlyCounts, 1);
    
    container.innerHTML = `
        <div class="page-header">
            <div>
                <h1 class="page-title">Analytics</h1>
                <p class="page-subtitle">Deep dive into group dynamics</p>
            </div>
        </div>
        
        <div class="grid-3" style="margin-bottom: 2rem;">
            <div class="card">
                <div class="stat-label">Group Dynamics</div>
                <div style="margin-top: 1rem;">
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;"><span>Avg Msgs/Participant</span> <strong>${a.foundMembers > 0 ? Math.round(a.totalMessages/a.foundMembers) : 0}</strong></div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;"><span>Active Participation</span> <strong>${a.totalMembers > 0 ? Math.round((a.foundMembers/a.totalMembers)*100) : 0}%</strong></div>
                    <div style="display:flex; justify-content:space-between;"><span>Silence Rate</span> <strong>${a.totalMembers > 0 ? Math.round((a.silentMembers/a.totalMembers)*100) : 0}%</strong></div>
                </div>
            </div>
            <div class="card">
                <div class="stat-label">Time Analysis</div>
                <div style="margin-top: 1rem;">
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;"><span>Busiest Hour</span> <strong>${a.hourlyCounts.indexOf(maxHourly)}:00</strong></div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;"><span>Active Days</span> <strong>${a.activeDays}</strong></div>
                    <div style="display:flex; justify-content:space-between;"><span>Date Span</span> <strong>${a.dateRange}</strong></div>
                </div>
            </div>
            <div class="card">
                <div class="stat-label">Word Analysis</div>
                <div style="margin-top: 1rem;">
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;"><span>Total Words</span> <strong>${a.totalWords}</strong></div>
                    <div style="display:flex; justify-content:space-between; margin-bottom:0.5rem;"><span>Avg Words/Msg</span> <strong>${a.avgWordsPerMsg}</strong></div>
                    <div style="display:flex; justify-content:space-between;"><span>Total Media</span> <strong>${a.totalMedia}</strong></div>
                </div>
            </div>
        </div>
        
        <div class="card">
            <h2 class="card-title">Hourly Activity Volume</h2>
            <div class="chart-container" style="height: 250px;">
                ${a.hourlyCounts.map((val, i) => {
                    let pct = (val / maxHourly) * 100;
                    return `<div class="chart-bar" style="height: ${pct}%;" title="${i}:00 - ${val} msgs">
                        <div style="position: absolute; bottom: -25px; left: 50%; transform: translateX(-50%); font-size: 10px; color: var(--text-muted);">${i}</div>
                    </div>`;
                }).join('')}
            </div>
            <div style="height: 30px;"></div>
        </div>
    `;
}

function renderReports() {
    const container = document.getElementById('page-container');
    const a = AppState.analytics;
    
    container.innerHTML = `
        <div class="page-header" id="report-header">
            <div>
                <h1 class="page-title">Summary Report</h1>
                <p class="page-subtitle">Generated on ${new Date().toLocaleDateString()}</p>
            </div>
            <button class="btn btn-primary" onclick="window.print()"><span class="material-icons-outlined">print</span> Print PDF</button>
        </div>
        
        <div class="printable-report">
            <div class="card" style="margin-bottom: 2rem;">
                <h2 style="margin-bottom: 1rem; border-bottom: 2px solid var(--border); padding-bottom: 0.5rem;">Executive Summary</h2>
                <p style="margin-bottom: 1rem;">This report covers WhatsApp group activity from <strong>${a.dateRange}</strong>.</p>
                <p style="margin-bottom: 1rem;">During this period, there were <strong>${a.activeDays}</strong> active days with an average of <strong>${a.avgMsgsPerDay}</strong> messages per day. The total message volume reached <strong>${a.totalMessages}</strong> messages, containing approximately <strong>${a.totalWords}</strong> words and <strong>${a.totalMedia}</strong> media files.</p>
                <p>The group currently has <strong>${a.totalMembers}</strong> members, out of which <strong>${a.foundMembers}</strong> actively participated and <strong>${a.silentMembers}</strong> remained completely silent in the exported history.</p>
            </div>
            
            <div class="grid-2">
                <div class="card">
                    <h3 style="margin-bottom: 1rem; color: var(--text-muted); text-transform: uppercase; font-size: 0.85rem;">Key Metrics</h3>
                    <ul style="list-style: none; padding: 0;">
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border);"><span>Total Messages</span> <strong>${a.totalMessages}</strong></li>
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border);"><span>Total Words</span> <strong>${a.totalWords}</strong></li>
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border);"><span>Total Media</span> <strong>${a.totalMedia}</strong></li>
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0;"><span>Active Participants</span> <strong>${a.foundMembers} / ${a.totalMembers}</strong></li>
                    </ul>
                </div>
                <div class="card">
                    <h3 style="margin-bottom: 1rem; color: var(--text-muted); text-transform: uppercase; font-size: 0.85rem;">Time Insights</h3>
                    <ul style="list-style: none; padding: 0;">
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border);"><span>Active Days</span> <strong>${a.activeDays}</strong></li>
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border);"><span>Avg Messages / Day</span> <strong>${a.avgMsgsPerDay}</strong></li>
                        <li style="display:flex; justify-content:space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--border);"><span>Peak Activity Hour</span> <strong>${a.hourlyCounts.indexOf(Math.max(...a.hourlyCounts, 1))}:00</strong></li>
                    </ul>
                </div>
            </div>
        </div>
    `;
}


// Router
function handleRoute() {
    let hash = window.location.hash.replace('#', '');
    if (!hash) hash = 'dashboard';

    document.querySelectorAll('.nav-item').forEach(el => {
        if (el.getAttribute('href') === '#' + hash || (hash.startsWith('profile-') && el.getAttribute('href') === '#members')) {
            el.classList.add('active');
        } else {
            el.classList.remove('active');
        }
    });

    if (hash === 'dashboard') renderDashboard();
    else if (hash === 'members') renderMembers();
    else if (hash.startsWith('profile-')) renderProfile(hash.split('-')[1]);
    else if (hash === 'messages') renderMessages();
    else if (hash === 'gallery') renderGallery();
    else if (hash === 'analytics') renderAnalytics();
    else if (hash === 'reports') renderReports();
    else renderDashboard();
}

// Init
document.addEventListener('DOMContentLoaded', async () => {
    await loadData();
    window.addEventListener('hashchange', handleRoute);
    handleRoute();

    document.getElementById('theme-toggle').addEventListener('click', () => {
        const root = document.documentElement;
        const isDark = root.getAttribute('data-theme') === 'dark';
        root.setAttribute('data-theme', isDark ? 'light' : 'dark');
        document.querySelector('#theme-toggle .material-icons-outlined').textContent = isDark ? 'dark_mode' : 'light_mode';
    });
});
