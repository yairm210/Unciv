/**
 * This is the main entrypoint to your Probot app
 * @param {import('probot').Application} app
 */
module.exports = app => {
  // Your code here
  app.log('Yay, the app was loaded!')

  app.on('issue_comment.created', async context => {
    //const issueComment = context.issue({ body: 'Thanks for opening this issue!' })
    app.log("Comment created")
    var translations = "translations"
    var owner = context.repo({}).owner

    if(context.payload.comment.body!="merge translations") return
    if(context.payload.comment.user!=owner){
      return await context.github.issues.createComment(context.issue({ body: 'Do not meddle in the affairs of wizards' }))
    }

    if(!await branchExists(context, translations)) createTranslationBranch(context)

    var translationPulls = await context.github.pulls.list(context.repo({state:"open", head:owner+":"+translations}))
    if (translationPulls.data.length == 0){
      var defaultBranch = await getDefaultBranch(context)
      await context.github.pulls.create(context.repo({title:"Translations update", head:translations, base:defaultBranch}))
      await context.github.issues.createComment(context.issue({ body: 'Translations PR created' }))
    }

    var ourPr = await context.github.pulls.get(context.repo({pull_number:context.payload.issue.number}))
    console.log("Label: "+ourPr.data.base.label)
    console.log("Translations branch: "+context.repo({}).owner+":"+translations)
    if(ourPr.data.base.label != context.repo({}).owner+":"+translations) {
      await context.github.pulls.update(context.repo({pull_number:context.payload.issue.number, base:translations}),)
    }
    // else await context.github.issues.createComment(context.issue({ body: 'Already in '+translations }))
    if(ourPr.data.state=="open" && ourPr.data.mergeable){
      await context.github.pulls.merge(context.repo({pull_number:context.payload.issue.number, merge_method:"squash"}))
    }
    else await context.github.issues.createComment(context.issue({ body: 'Not mergable' }))
  })

  async function branchExists(context, branchName){
    try {
      await context.github.git.getRef(context.repo({ref:'heads/'+branchName}))
      return true
    } catch (err) {
      return false
    }
  }

  async function getDefaultBranch(context){
    var repo = await context.github.repos.get(context.repo())
    return repo.data.default_branch
  }

  async function createTranslationBranch(context){
    var defaultBranch = await getDefaultBranch(context)
    var currentHead = await context.github.git.getRef(context.repo({ref:'heads/'+defaultBranch}))
    var currentSha = currentHead.data.object.sha
    app.log("Current sha: "+currentSha)
    var newBranch = await context.github.git.createRef(context.repo({ref: `refs/heads/translations`, sha: currentSha}))
    await context.github.issues.createComment(context.issue({ body: 'Translations branch created' }))
  }

  // For more information on building apps:
  // https://probot.github.io/docs/

  // To get your app running against GitHub, see:
  // https://probot.github.io/docs/development/
}
