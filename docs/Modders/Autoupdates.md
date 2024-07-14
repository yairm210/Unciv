# Autoupdates

Unciv contains built-in capabilities for packing images and autoupdating uniques.

You can automate these updates by adding a GitHub Actions workflow to your repository.

- In your Github page go to "actions" tab
- Suggested for this repo > Simple workflow > "Configure"
- Copy the text of [this file](https://github.com/yairm210/Unciv-IV-mod/blob/master/.github/workflows/autoupdate.yml) ("copy raw file") to the new file in your repo
- Change the file name to "autoupdate.yml" at the top
- "Commit changes" (green button, top-right) > "Commit changes"

On every commit, and once per day, it will:

- Try to shrink PNGs (this is lossless, the pixel data remains the same but the file takes less space)
- Try to autoupdate deprecated uniques

If there are changes, this will create a PR to your repo - [here's an example](https://github.com/yairm210/Unciv-IV-mod/pull/31) - which you can choose to accept

If you see that the autoupdate isn't 100% - in which case talk to me and we'll sort it out 🙂
