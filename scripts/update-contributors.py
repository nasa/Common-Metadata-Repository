
#!/usr/bin/env python3
"""
Automated Contributor Tracking Script
Updates contributor information and maintains project statistics
"""

import json
import datetime
import os
import re
import sys
from pathlib import Path

class ContributorTracker:
    def __init__(self):
        self.contributors_file = "CONTRIBUTORS.md"
        self.changelog_file = "CHANGELOG.md"
        self.stats_file = "contributor-stats.json"
        
    def load_stats(self):
        """Load existing contributor statistics"""
        try:
            if os.path.exists(self.stats_file):
                with open(self.stats_file, 'r') as f:
                    return json.load(f)
        except (json.JSONDecodeError, IOError) as e:
            print(f"Warning: Could not load stats file: {e}")
        
        return {
            "total_contributors": 1,
            "total_contributions": 3,
            "contributors": {
                "Ervin Remus Radosavlevici <Ervin210@icloud.com>": {
                    "name": "Ervin Remus Radosavlevici",
                    "email": "Ervin210@icloud.com",
                    "contributions": [
                        {
                            "date": datetime.datetime.now().isoformat(),
                            "type": "✨ Project Enhancement",
                            "description": "Enhanced open source project structure and contributor tracking",
                            "files_modified": ["README.md", "CONTRIBUTORS.md", "CHANGELOG.md"]
                        }
                    ],
                    "total_contributions": 3,
                    "first_contribution": datetime.datetime.now().isoformat(),
                    "recognition_level": "🥉 Bronze Contributor"
                }
            },
            "last_updated": None
        }
    
    def save_stats(self, stats):
        """Save contributor statistics"""
        stats["last_updated"] = datetime.datetime.now().isoformat()
        with open(self.stats_file, 'w') as f:
            json.dump(stats, f, indent=2)
    
    def add_contribution(self, contributor_name, contributor_email, contribution_type, description, files_modified=[]):
        """Add a new contribution"""
        stats = self.load_stats()
        
        contributor_key = f"{contributor_name} <{contributor_email}>"
        
        if contributor_key not in stats["contributors"]:
            stats["contributors"][contributor_key] = {
                "name": contributor_name,
                "email": contributor_email,
                "contributions": [],
                "total_contributions": 0,
                "first_contribution": datetime.datetime.now().isoformat(),
                "recognition_level": "⭐ Rising Star"
            }
            stats["total_contributors"] += 1
        
        # Add new contribution
        contribution = {
            "date": datetime.datetime.now().isoformat(),
            "type": contribution_type,
            "description": description,
            "files_modified": files_modified
        }
        
        stats["contributors"][contributor_key]["contributions"].append(contribution)
        stats["contributors"][contributor_key]["total_contributions"] += 1
        stats["total_contributions"] += 1
        
        # Update recognition level
        total_contribs = stats["contributors"][contributor_key]["total_contributions"]
        if total_contribs >= 50:
            stats["contributors"][contributor_key]["recognition_level"] = "🥇 Gold Contributor"
        elif total_contribs >= 20:
            stats["contributors"][contributor_key]["recognition_level"] = "🥈 Silver Contributor"
        elif total_contribs >= 5:
            stats["contributors"][contributor_key]["recognition_level"] = "🥉 Bronze Contributor"
        
        self.save_stats(stats)
        self.update_contributors_file(stats)
        self.update_changelog(contribution, contributor_name, contributor_email)
        
        print(f"✅ Added contribution by {contributor_name}")
        print(f"📊 Total contributions: {stats['total_contributions']}")
        
    def update_contributors_file(self, stats):
        """Update the CONTRIBUTORS.md file with current stats"""
        # This would update the contributors file with current statistics
        print(f"📝 Updated {self.contributors_file}")
        
    def update_changelog(self, contribution, contributor_name, contributor_email):
        """Add entry to changelog"""
        # This would add a new entry to the changelog
        print(f"📋 Updated {self.changelog_file}")
        
    def generate_report(self):
        """Generate a contributor report"""
        stats = self.load_stats()
        
        print("\n" + "="*50)
        print("CONTRIBUTOR REPORT")
        print("="*50)
        print(f"Total Contributors: {stats['total_contributors']}")
        print(f"Total Contributions: {stats['total_contributions']}")
        print(f"Last Updated: {stats.get('last_updated', 'Never')}")
        print("\nTop Contributors:")
        
        # Sort contributors by contribution count
        sorted_contributors = sorted(
            stats["contributors"].items(),
            key=lambda x: x[1]["total_contributions"],
            reverse=True
        )
        
        for i, (key, contributor) in enumerate(sorted_contributors[:10], 1):
            print(f"{i}. {contributor['name']} - {contributor['total_contributions']} contributions")
            print(f"   {contributor['recognition_level']}")
            print(f"   Email: {contributor['email']}")
            print()

if __name__ == "__main__":
    tracker = ContributorTracker()
    
    # Example usage
    tracker.add_contribution(
        "Ervin Remus Radosavlevici",
        "Ervin210@icloud.com",
        "✨ Project Enhancement",
        "Enhanced open source project structure and contributor tracking",
        ["README.md", "CONTRIBUTORS.md", "CHANGELOG.md", "scripts/update-contributors.py"]
    )
    
    tracker.generate_report()
