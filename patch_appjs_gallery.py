import sys
import re

with open('app/src/main/assets/report_template/js/app.js', 'r') as f:
    content = f.read()

# We need to replace the entire renderGallery() function
start_marker = "function renderGallery() {"
end_marker = "function renderAnalytics() {"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx == -1 or end_idx == -1:
    print("Could not find gallery bounds")
    sys.exit(1)

new_render_gallery = """function renderGallery() {
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
            contentHtml = `<img src="${filePath}" onerror="this.outerHTML='<div class=\\'media-missing\\'>Missing</div>'" style="width:100%; height:100%; object-fit:cover;">`;
        } else if (type === 'VIDEO') {
            contentHtml = `<video src="${filePath}" onerror="this.outerHTML='<div class=\\'media-missing\\'>Missing</div>'" controls style="width:100%; height:100%; object-fit:cover;"></video>`;
        } else if (type === 'AUDIO' || type === 'VOICE') {
            contentHtml = `<div style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; padding:1rem; background:var(--surface);">
                <span class="material-icons-outlined" style="font-size:2rem; margin-bottom:0.5rem">audiotrack</span>
                <audio src="${filePath}" onerror="this.parentElement.innerHTML='<div class=\\'media-missing\\'>Missing</div>'" controls style="width:100%; height:32px;"></audio>
            </div>`;
        } else {
            contentHtml = `<div style="display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; background:var(--surface);">
                <span class="material-icons-outlined" style="font-size:3rem;">insert_drive_file</span>
                <span style="margin-top:0.5rem; text-align:center; font-size:0.8rem; word-break:break-all; padding: 0 0.5rem;">${m.fileName}</span>
                <a href="${filePath}" target="_blank" onerror="this.outerHTML='<div class=\\'media-missing\\'>Missing</div>'" style="margin-top:0.5rem; font-size:0.8rem;">Open File</a>
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

"""

content = content[:start_idx] + new_render_gallery + content[end_idx:]

with open('app/src/main/assets/report_template/js/app.js', 'w') as f:
    f.write(content)
