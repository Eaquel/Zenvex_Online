"""V19: artık kullanılmayan sınır görsellerini atlastan siler (bölge kenarı yalnız bulut).

python3 tools/assetgen/v19_prune.py
  s/b_<bölge>_<n> (sınır parçaları), karantina duvarı / direği / tabelası, sınır projektörü ve kemikleri
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from atlas_patch import Atlas  # noqa: E402

RETIRED = ("s/quarantine_wall", "s/quarantine_post", "s/quarantine_sign", "s/border_searchlight", "s/border_bones")


def gone(k):
    return k.startswith("s/b_") or k in RETIRED


def main():
    A = Atlas()
    groups = {i: g for g, ids in A.m["groups"].items() for i in ids}
    touched = {groups[v[0]] for k, v in A.m["sprites"].items() if gone(k)}
    print("silinecek:", sum(1 for k in A.m["sprites"] if gone(k)), "grup:", touched)
    for g in touched:
        A.patch(g, drop=gone)
    A.save()


if __name__ == "__main__":
    main()
