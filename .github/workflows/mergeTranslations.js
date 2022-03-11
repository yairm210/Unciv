
const { Octokit } = require("@octokit/rest");
const { version } = require("os");
const internal = require("stream");
const fs = require("fs");
const { argv } = require("process");


// To be run from the main Unciv repo directory
// Summarizes and adds the summary to the changelog.md file
// Meant to be run from a Github action as part of the preparation for version rollout

async function main(){

    var args = argv.slice(2) // remove 'node' and filename parameters

    var auth = args[0]
    var issue_to_comment_on = 0 // 0 means no issue
    if (args[1]) issue_to_comment_on = Number(args[1])


    var branch_to_merge_to = "translations"
    if(args[2]) branch_to_merge_to = args[2]


    const github = new Octokit({
        auth: auth
    });
    
            
    const repo = {
        owner: "yairm210",
        repo: "Unciv" }
  
  async function branchExists(branchName) {
    try {
      await github.git.getRef({...repo, ref: 'heads/' + branchName })
      return true
    } catch (err) {
      return false
    }
  }

  async function getDefaultBranch() {
    var repoData = await github.repos.get(repo)
    return repoData.data.default_branch
  }
  

  async function createTranslationBranchIfNeeded() {
    if (await branchExists(branch_to_merge_to)) return
    var defaultBranch = await getDefaultBranch()
    
    var currentHead = await github.git.getRef({...repo, ref: 'heads/' + defaultBranch })
    
    var currentSha = currentHead.data.object.sha
    console.log("Current sha: " + currentSha)
    
    await github.git.createRef({...repo,
      ref: `refs/heads/` + branch_to_merge_to,
      sha: currentSha })
    
    if (issue_to_comment_on != 0)
        await github.issues.createComment({...repo,
        issue_number: issue_to_comment_on,
        body: 'Translations branch created' })
  }
  
  async function mergeExistingTranslationsIntoBranch(){
    var translationPrs = await github.pulls.list({ ...repo, state: "open" })
    
    // When we used a forEach loop here, only one merge would happen at each run,
    //  because we essentially started multiple async tasks in parallel and they conflicted.
    // Instead, we use X of Y as per https://stackoverflow.com/questions/37576685/using-async-await-with-a-foreach-loop
    for (const pr of translationPrs.data) {
      if (pr.labels.some(label => label.name == "mergeable translation"))
        await tryMergePr(pr)
    }
  }
  
  async function tryMergePr(pr){
    if (pr.base.ref != branch_to_merge_to)
      await github.pulls.update({ ...repo,
        pull_number: pr.number,
        base: branch_to_merge_to })
    
    try {
      await github.pulls.merge({...repo,
        pull_number: pr.number,
        merge_method: "squash" })
      console.log("Merged #"+pr.number+", "+pr.title)
    } catch (err) {
      console.log(err)
    }
      
  }
              
  
  async function createTranslationPrIfNeeded() {
    var translationPulls = await github.pulls.list({...repo,
        state: "open",
        head: repo.owner + ":" + branch_to_merge_to });

    if (translationPulls.data.length == 0) { // no pr exists yet
      var defaultBranch = await getDefaultBranch();
      var result = await github.pulls.create({...repo,
        title: "Version rollout",
        head: branch_to_merge_to,
        base: defaultBranch });

        if (issue_to_comment_on != 0)
            await github.issues.createComment({...repo,
                issue_number: issue_to_comment_on,
                body: 'Version rollout PR created' });

        // await github.pulls.merge({...repo,
        //     pull_number: result.data.number,
        //     merge_method: "squash"
        // })
    }
  }
            
  await createTranslationBranchIfNeeded()
  await mergeExistingTranslationsIntoBranch()
  await createTranslationPrIfNeeded()
   
}

main()