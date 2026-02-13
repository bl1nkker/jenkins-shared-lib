import logging
import json
from datetime import datetime, timedelta, timezone
from utils import BitbucketAPIClient, get_env

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    
    git_url = get_env("GIT_URL")
    git_user = get_env("GIT_USER")
    git_password = get_env("GIT_PASSWORD")

    client = BitbucketAPIClient(baseurl=git_url, username=git_user, password=git_password)

    cutoff_date = (datetime.now(timezone.utc) - timedelta(days=30)).date()

    project_keys = client.list_project_keys()
    stale_branches = []
    for project in project_keys:
        repo_slugs = client.list_repository_slugs(project_key=project)
        for repo in repo_slugs:
            branches = client.list_branches(repo_slug=repo, project_key=project)
            for branch_name, branch_commit in branches.items():
                if client.check_branch_stale(project_key=project, repo_slug=repo, branch_name=branch_name, commit_hash=branch_commit, cutoff_date=cutoff_date):
                    stale_branches.append({"project": project, "repository": repo, "branch": branch_name, "commit": branch_commit})

    output_file = "stale_branches.json"

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(stale_branches, f, indent=4)

    logging.info("Written %d stale branches to %s", len(stale_branches), output_file)
