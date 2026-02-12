import logging
import posixpath
import requests
import urllib3
import os
import argparse
import enum
from datetime import datetime, timedelta, timezone

urllib3.disable_warnings()

class BitbucketAPIClient():
    def __init__(self, baseurl: str, username: str, password: str):
        self.session = requests.session()
        self.baseurl = baseurl
        self.username = username
        self.password = password
        self.session.auth = (self.username, self.password)
        self.session.headers = {'Content-Type': 'application/json'}
        self.session.verify = False

    class Constants(enum.Enum):
        API_V1_PROJECTS = 'rest/api/1.0/projects?name={}'
        API_V1_REPOSITORIES = 'rest/api/1.0/projects/{}/repos?start={}'
        API_V1_BRANCHES = 'rest/api/1.0/projects/{}/repos/{}/branches?start={}'
        API_V1_COMMITS = 'rest/api/1.0/projects/{}/repos/{}/commits/{}'
        API_V1_COMPARE_BRANCHES = 'rest/api/1.0/projects/{}/repos/{}/compare/commits?from={}&to={}'
    # TODO: Get a better name
    PROJECT_NAMES = ['CDT Infrastructure']
    PREMIUM_BRANCHES = ['master', 'staging']

    def list_project_keys(self) -> list[str]:
        project_keys = []
        for project_name in self.PROJECT_NAMES:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_PROJECTS.value.format(project_name)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                # TODO: Find better message
                logging.error("Unable to fetch key for %s project, error: %s", project_name, err)
                continue
            data = res.json()
            if not data.get('values') or len(data.get('values')) > 1:
                # TODO: Find better message
                logging.error("Empty or too much values for %s project", project_name)
                continue
            project_keys.append(data["values"][0]["key"])
        return project_keys

    def list_project_repo_slugs(self, project_key: str) -> list[str]:
        page_number = 0
        repo_slugs = []
        while True:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_REPOSITORIES.value.format(project_key, page_number)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch project repositories for '%s' project, error: %s", project_key, err)
                return []
            data = res.json()
            for element in data['values']:
                repo_slugs.append(element['slug'])
            if data['isLastPage']:
                break
            page_number = data['nextPageStart']
        return repo_slugs

    def list_repo_branches(self, project_key: str, repo_slug: str):
        page_number = 0
        branches = {}
        while True:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_BRANCHES.value.format(project_key, repo_slug, page_number)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch branches for '%s' repository, error: %s", repo_slug, err)
                return {}
            data = res.json()
            for element in data['values']:
                # if branch is not default and not staging or master, then we can try to kill it
                if not element["isDefault"] and element["displayId"] not in self.PREMIUM_BRANCHES:
                    branches[element["displayId"]] = element["latestCommit"]
            if data['isLastPage']:
                break
            page_number = data['nextPageStart']
        return branches


    def _is_branch_stale(self, project_key: str, repo_slug: str, commit_hash: str, cutoff_date, branch_name) -> bool:
        
        for m_branch in self.PREMIUM_BRANCHES:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_COMPARE_BRANCHES.value.format(project_key, repo_slug, branch_name, m_branch)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                # if some shit happens -> just mark the branch as active
                logging.error("Unable to fetch branch info, error: %s", err)
                return False
            data = res.json()
            # if it is 0, then it can be deleted, if it is not, then it must not be deleted
            if data['size'] == 0:
                logging.info("Branch %s is already merged into %s", branch_name, m_branch)
                return True

        logging.info("Branch %s seems active", branch_name)

        # check if commit.committerTimestamp is valid
        res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_COMMITS.value.format(project_key, repo_slug, commit_hash)))
        try:
            res.raise_for_status()
        except requests.exceptions.HTTPError as err:
            # if some shit happens -> just mark the branch as active
            logging.error("Unable to fetch repository commit, error: %s", err)
            return False
        data = res.json()
        commit_date = datetime.fromtimestamp(data['committerTimestamp'] / 1000, tz=timezone.utc).date()
        if commit_date > cutoff_date:
            # if branch commit date is older than cutoff date - the branch is active
            logging.info("Branch %s is totally active", branch_name)
            return False
        # otherwise the branch is stale
        logging.info("Branch %s is stale. Last time: %s", branch_name, commit_date)
        return True


def get_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Environment variable {name} must be set")
    return value


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

    logging.info("DRY_RUN: %s", args.dry_run)
    git_url = get_env("GIT_URL")
    git_user = get_env("GIT_USER")
    git_password = get_env("GIT_PASSWORD")
    logging.info("Got GIT_URL: %s", git_url)

    client = BitbucketAPIClient(baseurl=git_url, username=git_user, password=git_password)
    cutoff_date = (datetime.now(timezone.utc) - timedelta(days=7)).date()
    project_keys = client.list_project_keys()
    logging.info(f"Got projects: '{project_keys}'")
    for project in project_keys:
        repo_slugs = client.list_project_repo_slugs(project_key=project)
        logging.info(f"Listing repos for '{project}': '{repo_slugs}'")
        for repo in repo_slugs:
            branches = client.list_repo_branches(repo_slug=repo, project_key=project)
            for branch_name, branch_commit in branches.items():
                if client._is_branch_stale(project_key=project, repo_slug=repo, branch_name=branch_name, commit_hash=branch_commit, cutoff_date=cutoff_date):
                    # delete branch
                    if args.dry_run:
                        logging.info(f"DRY_RUN enabled. Branch to delete: {project}/{repo}/{branch_name}")
                    else:
                        logging.info(f"About to delete branch {project}/{repo}/{branch_name}...")

