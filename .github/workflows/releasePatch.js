const fs = require("fs");


// To be run from the main Unciv repo directory
// Increments the latest code version to include 'patch-X'
// Meant to be run from a Github action as part of patch release
// To test locally - `node .github/workflows/releasePatch.js`

//region Executed Code
(async () => {
    const [newVersion, newAppCodeNumber] = updateBuildConfig();
    updateGameVersion(newVersion, newAppCodeNumber);
    console.log(newVersion)
})();
//endregion


//region Function Definitions

function getNextPatchVersion(currentVersion){
    if (currentVersion.match(/^\d+\.\d+\.\d+$/)) return currentVersion + "-patch1"
    var patchVersionRegexMatch = currentVersion.match(/^(\d+\.\d+\.\d+)-patch(\d+)$/)
    if (!patchVersionRegexMatch) throw "Unrecognizable version format!"
    var patchVersion = parseInt(patchVersionRegexMatch[2]) + 1
    return patchVersionRegexMatch[1] + "-patch" + patchVersion
}


function updateBuildConfig() {
    var buildConfigPath = "buildSrc/src/main/kotlin/BuildConfig.kt";
    var buildConfigString = fs.readFileSync(buildConfigPath).toString();

//    console.log("Original: " + buildConfigString);

    // Javascript string.match returns a regex string array, where array[0] is the entirety of the captured string,
    //  and array[1] is the first group, array[2] is the second group etc.

    var appVersionMatch = buildConfigString.match(/appVersion = "(.*)"/);
    const curVersion = appVersionMatch[1];
    const newVersion = getNextPatchVersion(curVersion)
//    console.log("New version: "+newVersion)

    buildConfigString = buildConfigString.replace(appVersionMatch[0], appVersionMatch[0].replace(curVersion, newVersion));
    var appCodeNumberMatch = buildConfigString.match(/appCodeNumber = (\d*)/);
    let currentAppCodeNumber = appCodeNumberMatch[1];
//    console.log("Current incremental version: " + currentAppCodeNumber);
    const nextAppCodeNumber = Number(currentAppCodeNumber) + 1;
//    console.log("Next incremental version: " + nextAppCodeNumber);
    buildConfigString = buildConfigString.replace(appCodeNumberMatch[0],
        appCodeNumberMatch[0].replace(currentAppCodeNumber, nextAppCodeNumber));

//    console.log("Final: " + buildConfigString);
    fs.writeFileSync(buildConfigPath, buildConfigString);
    return [newVersion, nextAppCodeNumber];
}

function updateGameVersion(newVersionString, newAppCodeNumber) {
    const gameInfoPath = "core/src/com/unciv/UncivGame.kt";
    const gameInfoSource = fs.readFileSync(gameInfoPath).toString();
    const regexp = /(\/\/region AUTOMATICALLY GENERATED VERSION DATA - DO NOT CHANGE THIS REGION, INCLUDING THIS COMMENT)[\s\S]*(\/\/endregion)/;
    const withNewVersion = gameInfoSource.replace(regexp, function(match, grp1, grp2) {
        const versionClassStr = createVersionClassString(newVersionString, newAppCodeNumber);
        return `${grp1}\n        val VERSION = ${versionClassStr}\n        ${grp2}`;
    })
    fs.writeFileSync(gameInfoPath, withNewVersion);
}

function createVersionClassString(newVersionString, newAppCodeNumber) {
    return `Version("${newVersionString}", ${newAppCodeNumber})`;
}

//endregion
