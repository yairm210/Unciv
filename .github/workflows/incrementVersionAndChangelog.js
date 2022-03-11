
const { Octokit } = require("@octokit/rest");
const { version } = require("os");
const internal = require("stream");
const fs = require("fs")


// To be run from the main Unciv repo directory
// Summarizes and adds the summary to the changelog.md file
// Meant to be run from a Github action as part of the preparation for version rollout

async function main(){

    // no need to add auth: token since we're only reading from the commit list, which is public anyway
    const octokit = new Octokit({});
    

    var result = await octokit.repos.listCommits({
        owner: "yairm210",
        repo: "Unciv",
        per_page: 50 })
    

    var commitSummary = "";
    var ownerToCommits = {}
    var reachedPreviousVersion = false
    var nextVersionString = ""
    result.data.forEach(commit => {
        if (reachedPreviousVersion) return
        var author = commit.author.login
        if (author=="uncivbot[bot]") return
        var commitMessage = commit.commit.message.split("\n")[0];

        var versionMatches = commitMessage.match(/^\d+\.\d+\.(\d+)$/)
        if (versionMatches){ // match EXACT version, like 3.4.55  ^ is for start-of-line, $ for end-of-line
            reachedPreviousVersion=true
            var minorVersion = Number(versionMatches[1])
            console.log("Previous version: "+commitMessage)
            nextVersionString = commitMessage.replace(RegExp(minorVersion+"$"), minorVersion+1 )
            console.log("Next version: " + nextVersionString)
            return
        }
        if (commitMessage.startsWith("Merge ") || commitMessage.startsWith("Update ")) return
        commitMessage = commitMessage.replace(/\(\#\d+\)/,"").replace(/\#\d+/,"") // match PR auto-text, like (#2345) or just #2345
        if (author != "yairm210"){
            if (ownerToCommits[author] == undefined) ownerToCommits[author]=[]
            ownerToCommits[author].push(commitMessage)
            }
            else commitSummary += "\n\n" + commitMessage
        }
    );
    
    Object.entries(ownerToCommits).forEach(entry => {
        const [author, commits] = entry;
        if (commits.length==1) commitSummary += "\n\n" + commits[0] + " - By "+author
        else {
        commitSummary += "\n\nBy "+author+":"
        commits.forEach(commitMessage => { commitSummary += "\n- "+commitMessage })
        }
    })
    console.log(commitSummary)

    var textToAddToChangelog = "## "+ nextVersionString + commitSummary + "\n\n"

    var changelogPath = 'changelog.md'
    var currentChangelog = fs.readFileSync(changelogPath).toString()
    if (!currentChangelog.startsWith(textToAddToChangelog)){ // minor idempotency - don't add twice
        var newChangelog = textToAddToChangelog + currentChangelog
        fs.writeFileSync(changelogPath, newChangelog)
    }

    var buildConfigPath = "buildSrc/src/main/kotlin/BuildConfig.kt"
    var buildConfigString = fs.readFileSync(buildConfigPath).toString()

    console.log("Original: "+buildConfigString)

    // node.js string.match returns a regex string array, where array[0] is the entirety of the captured string, 
    //  and array[1] is the first group, array[2] is the second group etc.  

    var appVersion = buildConfigString.match(/appVersion = "(.*)"/)
    if (appVersion != nextVersionString){
        buildConfigString = buildConfigString.replace(appVersion[0], appVersion[0].replace(appVersion[1], nextVersionString))
        var androidVersion = buildConfigString.match(/appCodeNumber = (\d*)/)
        console.log("Android version: "+androidVersion)
        var nextAndroidVersion = Number(androidVersion[1]) + 1
        console.log("Next Android version: "+ nextAndroidVersion)
        buildConfigString = buildConfigString.replace(androidVersion[0],
             androidVersion[0].replace(androidVersion[1], nextAndroidVersion))

        console.log("Final: "+buildConfigString)
        fs.writeFileSync(buildConfigPath, buildConfigString)
    }
   
}

main()