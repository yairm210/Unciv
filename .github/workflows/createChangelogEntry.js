
const { Octokit } = require("@octokit/rest");

//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 

async function main(){

    const myArgs = process.argv.slice(2) // see https://nodejs.org/en/knowledge/command-line/how-to-parse-command-line-arguments/
    const octokit = new Octokit({
        auth: myArgs[0],
    });

    var result = await octokit.repos.listCommits({
        owner: "yairm210",
        repo: "Unciv",
        per_page: 50 })
    

//   var result = await getCommits()

    var commitSummary = "";
    var ownerToCommits = {}
    var reachedPreviousVersion = false
    result.data.forEach(commit => {
        if (reachedPreviousVersion) return
        var author = commit.author.login
        if (author=="uncivbot[bot]") return
        var commitMessage = commit.commit.message.split("\n")[0];

        if (commitMessage.match(/^\d+\.\d+\.\d+$/)){ // match EXACT version, like 3.4.55  ^ is for start-of-line, $ for end-of-line
        reachedPreviousVersion=true
        console.log("Previous version: "+commitMessage)
        return
        }
        if (commitMessage.startsWith("Merge ") || commitMessage.startsWith("Update ")) return
        commitMessage = commitMessage.replace(/\(\#\d+\)/,"") // match PR auto-text, like (#2345)
        if (author != "yairm210"){
        if (ownerToCommits[author] == undefined) ownerToCommits[author]=[]
        ownerToCommits[author].push(commitMessage)
        }
        else commitSummary += "\n\n" + commitMessage
    });
    Object.entries(ownerToCommits).forEach(entry => {
        const [author, commits] = entry;
        if (commits.length==1) commitSummary += "\n\n" + commits[0] + " - By "+author
        else {
        commitSummary += "\n\nBy "+author+":"
        commits.forEach(commitMessage => { commitSummary += "\n- "+commitMessage })
        }
    })
    console.log(commitSummary)

}

main()