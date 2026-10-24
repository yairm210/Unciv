import {Octokit} from "@octokit/rest";
import fs from "fs";
import {argv} from "process";

// To be run from the root of the Unciv repo (node .github/workflows/incrementVersionAndChangelog.mjs [authToken] [issueNumber])
// Summarizes and adds the summary to the changelog.md file
// Meant to be run from a Github action as part of the preparation for version rollout

const authToken = argv[2];
const issueNumber = argv[3] ? Number(argv[3]) : 0;

const repoCoords = { owner: "yairm210", repo: "Unciv" };

//region Executed Code
(async () => {
    if (authToken && await versionRolloutHasOpenPr()) {
        await mergeRemainingTranslations();
        return;
    }

    const versionAndChangelog = await parseCommits();
    const newVersionString = versionAndChangelog[0]
    const changelogString = versionAndChangelog[1]

    writeChangelog(newVersionString, changelogString)

    const newAppCodeNumber = updateBuildConfig(newVersionString);
    if (newAppCodeNumber){ // is false if buildConfig already contains the newVersionString
        createFastlaneFile(newAppCodeNumber, changelogString)
        updateGameVersion(newVersionString, newAppCodeNumber);
    }
})();
//endregion

//region Early-exit: merge remaining translations into existing version_rollout branch

async function versionRolloutHasOpenPr() {
    const github = new Octokit({ auth: authToken });
    try {
        await github.git.getRef({ ...repoCoords, ref: 'heads/version_rollout' });
    } catch (e) {
        return false;
    }
    const prs = await github.pulls.list({ ...repoCoords, state: 'open', head: repoCoords.owner + ':version_rollout' });
    return prs.data.length > 0;
}

async function mergeRemainingTranslations() {
    console.log("version_rollout branch already has an open PR — merging remaining translation PRs");
    const github = new Octokit({ auth: authToken });
    const allOpenPrs = await github.pulls.list({ ...repoCoords, state: 'open' });
    for (const pr of allOpenPrs.data) {
        if (pr.labels.some(label => label.name === 'mergeable translation'))
            await tryMergeTranslationPr(github, pr);
    }
    if (issueNumber) {
        await github.issues.createComment({ ...repoCoords, issue_number: issueNumber,
            body: 'Merged remaining translation PRs into existing version_rollout branch' });
    }
}

async function tryMergeTranslationPr(github, pr) {
    if (pr.base.ref !== 'version_rollout')
        await github.pulls.update({ ...repoCoords, pull_number: pr.number, base: 'version_rollout' });
    try {
        await github.pulls.merge({ ...repoCoords, pull_number: pr.number, merge_method: 'squash' });
        console.log("Merged #" + pr.number + ", " + pr.title);
    } catch (err) {
        console.log(err);
    }
}

//endregion


//region Function Definitions

// Finds the most recently released version by looking at git tags (the actual release marker),
// Returns: {sha, major, minor, patch} of the highest released version tag
async function getLatestVersionTag(octokit) {
    const tags = await octokit.paginate(octokit.repos.listTags, {
        owner: "yairm210", repo: "Unciv", per_page: 100
    });
    const versionTags = tags
        // Ignore patch versions
        .map(tag => ({ sha: tag.commit.sha, match: tag.name.match(/^(\d+)\.(\d+)\.(\d+)$/) }))
        .filter(tag => tag.match)
        .map(tag => ({
            sha: tag.sha,
            major: Number(tag.match[1]), minor: Number(tag.match[2]), patch: Number(tag.match[3])
        }));
    versionTags.sort((a, b) => b.major - a.major || b.minor - a.minor || b.patch - a.patch);
    return versionTags[0];
}

// Returns: [nextVersionString, changelogString]
async function parseCommits() {
    // no need to add auth: token since we're only reading from the commit list, which is public anyway
    const octokit = new Octokit({});

    const latestVersionTag = await getLatestVersionTag(octokit);
    if (!latestVersionTag) throw new Error("No version tags found on the repo");
    const nextVersionString = `${latestVersionTag.major}.${latestVersionTag.minor}.${latestVersionTag.patch + 1}`;
    console.log("Previous version tag commit: " + latestVersionTag.sha);
    console.log("Next version: " + nextVersionString);

    const result = await octokit.repos.listCommits({
        owner: "yairm210",
        repo: "Unciv",
        per_page: 50
    });

    let commitSummary = "";
    const ownerToCommits = {};
    let reachedPreviousVersion = false;
    result.data.forEach(commit => {
    // See https://github.com/yairm210/Unciv/actions/runs/4136712446/jobs/7151150557 for example of strange commit with null author
            if (reachedPreviousVersion || commit.author == null) return;
            if (commit.sha === latestVersionTag.sha) { // this is the last-released commit - stop here, don't include it
                reachedPreviousVersion = true;
                return;
            }
            const author = commit.author.login;
            if (author === "uncivbot[bot]") return;
            let commitMessage = commit.commit.message.split("\n")[0];

            if (commitMessage.startsWith("Merge ") || commitMessage.startsWith("Update ")) return;
            commitMessage = commitMessage.replace(/\(\#\d+\)/, "").replace(/\#\d+/, ""); // match PR auto-text, like (#2345) or just #2345
            if (author !== "yairm210") {
                if (!(author in ownerToCommits)) ownerToCommits[author] = [];
                ownerToCommits[author].push(commitMessage);
            } else {
                commitSummary += "\n\n" + commitMessage;
            }
        }
    );

    if (!reachedPreviousVersion) throw new Error("Did not find the previous version tag commit within the fetched commit history - fetch more commits");

    for (const [author, commits] of Object.entries(ownerToCommits)) {
        if (commits.length === 1) {
            commitSummary += "\n\n" + commits[0] + "- By " + author;
        } else {
            commitSummary += "\n\nBy " + author + ":";
            commits.forEach(commitMessage => { commitSummary += "\n- " + commitMessage });
        }
    }
    console.log(commitSummary);
    return [nextVersionString, commitSummary];
}

function writeChangelog(nextVersionString, changelogString){
    const textToAddToChangelog = "## " + nextVersionString + changelogString + "\n\n";

    const changelogPath = 'changelog.md';
    const currentChangelog = fs.readFileSync(changelogPath).toString();
    if (!currentChangelog.startsWith(textToAddToChangelog)) { // minor idempotency - don't add twice
        const newChangelog = textToAddToChangelog + currentChangelog;
        fs.writeFileSync(changelogPath, newChangelog);
    }
}

function updateBuildConfig(nextVersionString) {
    const buildConfigPath = "buildSrc/src/main/kotlin/BuildConfig.kt";
    let buildConfigString = fs.readFileSync(buildConfigPath).toString();

    console.log("Original: " + buildConfigString);

    // Javascript string.match returns a regex string array, where array[0] is the entirety of the captured string,
    //  and array[1] is the first group, array[2] is the second group etc.

    const appVersionMatch = buildConfigString.match(/appVersion = "(.*)"/);
    const curVersion = appVersionMatch[1];
    if (curVersion !== nextVersionString) {
        buildConfigString = buildConfigString.replace(appVersionMatch[0], appVersionMatch[0].replace(curVersion, nextVersionString));
        const appCodeNumberMatch = buildConfigString.match(/appCodeNumber = (\d*)/);
        let currentAppCodeNumber = appCodeNumberMatch[1];
        console.log("Current incremental version: " + currentAppCodeNumber);
        const nextAppCodeNumber = Number(currentAppCodeNumber) + 1;
        console.log("Next incremental version: " + nextAppCodeNumber);
        buildConfigString = buildConfigString.replace(appCodeNumberMatch[0],
            appCodeNumberMatch[0].replace(currentAppCodeNumber, nextAppCodeNumber));

        console.log("Final: " + buildConfigString);
        fs.writeFileSync(buildConfigPath, buildConfigString);
        return nextAppCodeNumber;
    }
    return false
}

function createFastlaneFile(newAppCodeNumber, changelogString){
    // A new, discrete changelog file for fastlane (F-Droid support):
    const fastlaneChangelogPath = "fastlane/metadata/android/en-US/changelogs/" + newAppCodeNumber + ".txt";
    fs.writeFileSync(fastlaneChangelogPath, changelogString);
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
