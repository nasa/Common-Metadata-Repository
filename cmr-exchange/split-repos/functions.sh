function project-to-repo-name () {
	REPO=$1
	echo $REPO|sed 's/-app$//'
}

function arg-check () {
	PROJ=$1
	if [[ -z "$PROJ" ]]; then
		echo "ERROR: You must provide a project directory."
		exit 127
	fi
}

function clone-repo () {
	START_REPO=$1
	REPO=$2
	git clone $START_REPO $REPO
	echo "Cloned start repo to $REPO."
}

function removal-check () {
	CONTINUE_FLAG=$1
	if [[ "$CONTINUE_FLAG" != "-y" ]]; then
		echo -n "Preparing to remove files in `pwd`; continue? [y/N]"
		read RESPONSE
		if [[ "$RESPONSE" != "y" ]]; then
			echo "Cancelled."
			exit 127
		fi
	fi
}

function remove-non-proj-files () {
	PROJ=$1
	find . -depth 1 ! -name "$PROJ" ! -name ".git" -exec rm -rfv {} \;
	echo "Removed non-$PROJ files."
}

function prune-repo () {
	PWD=$1
	git ls-files > $PWD/keep-these.txt
	echo "Created list of files whose git history should be kept."

	git filter-branch --force --index-filter \
	  "git rm  --ignore-unmatch --cached -qr . ; \
	  cat $PWD/keep-these.txt | xargs git reset -q \$GIT_COMMIT --" \
	  --prune-empty --tag-name-filter cat -- --all
	echo "Cleaned up git history."

	rm -rf .git/refs/original/ && \
	git reflog expire --expire=now --all && \
	git gc --aggressive --prune=now && \
	rm keep-these.txt
	echo "Cleaned up git internals."
}

function move-top-level () {
	PROJ=$1
	git mv -v $PROJ/* .
	rmdir $PROJ
	MSG="Moved $PROJ files to top-level."
	git commit -a -m "$MSG"
	echo "$MSG"
}

function publish () {
	GIT_REPO=$1
	git remote set-url origin git@github.com:nasa-cmr/${GIT_REPO}.git
	git push origin master -f
	echo "Pushed extracted code for $GIT_REPO to its own remote repo."
}
