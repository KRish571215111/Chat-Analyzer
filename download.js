const fs = require('fs');
const https = require('https');
const path = require('path');

const fontsDir = 'app/src/main/assets/report_template/fonts';
const iconsDir = 'app/src/main/assets/report_template/icons';

fs.mkdirSync(fontsDir, { recursive: true });
fs.mkdirSync(iconsDir, { recursive: true });

function download(url, dest) {
    return new Promise((resolve, reject) => {
        const file = fs.createWriteStream(dest);
        https.get(url, (response) => {
            response.pipe(file);
            file.on('finish', () => {
                file.close(resolve);
            });
        }).on('error', (err) => {
            fs.unlink(dest, () => reject(err));
        });
    });
}

async function run() {
    await download('https://fonts.gstatic.com/s/materialiconsoutlined/v109/gok-H7zzDkdnRel8-DQ6KAXJ69wP1tGnf4ZOScbUpvw.woff2', path.join(iconsDir, 'material-icons.woff2'));
    await download('https://fonts.gstatic.com/s/inter/v13/UcCO3FwrK3iLTeHuS_fvQtMwCp50KnMw2boKoduKmMEVuLyfMZhrib2Bg-4.ttf', path.join(fontsDir, 'inter.ttf'));
    console.log("Downloaded successfully");
}
run();
