import os
import json
import logging
import argparse
from utils import BitbucketAPIClient, get_env


def parse_args():
    parser = argparse.ArgumentParser(description="Cleanup bitbucket branches")
    parser.add_argument(
        "--dry-run",
        action="store_true"
    )
    return parser.parse_args()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    args = parse_args()
    git_url = get_env("GIT_URL")
    git_user = get_env("GIT_USER")
    git_password = get_env("GIT_PASSWORD")

    client = BitbucketAPIClient(baseurl=git_url, username=git_user, password=git_password)

    input_file = "stale_branches.json"
    if not os.path.exists(input_file):
        logging.error("File %s not found. Nothing to delete.", input_file)
        exit(1)

    with open(input_file, "r", encoding="utf-8") as f:
        stale_branches = json.load(f)

    logging.info("Loaded %d branches to process", len(stale_branches))

    for item in stale_branches:
        project = item["project"]
        repo = item["repository"]
        branch_name = item["branch"]

        if args.dry_run:
            logging.info("DRY_RUN enabled. Branch to delete: %s/%s/%s", project, repo, branch_name)
            continue

        logging.info("Branch to delete: %s/%s/%s", project, repo, branch_name)
        try:
            if repo == "ci-cd-infra-shared-pipeline-libraries" and branch_name == "CD-4527":
                logging.info("Definetely deleted branch %s/%s/%s", project, repo, branch_name)
                client.delete_branch(project_key=project, repo_slug=repo, branch_name=branch_name)
            logging.info("Successfully deleted branch %s/%s/%s", project, repo, branch_name)
        except Exception as e:
            logging.error("Failed to delete branch %s/%s/%s: %s", project, repo, branch_name, e)
