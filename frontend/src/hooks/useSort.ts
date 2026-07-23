"use client";

import { useMemo, useState } from "react";

export type SortDir = "asc" | "desc";

export function useSort<T>(
  data: T[],
  defaultKey?: string,
  getValue?: (item: T, key: string) => string | number,
) {
  const [sortKey, setSortKey] = useState<string | undefined>(defaultKey);
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  const toggleSort = (key: string) => {
    if (key === sortKey) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  const sortedData = useMemo(() => {
    if (!sortKey) return data;

    return [...data].sort((a, b) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const aVal = getValue ? getValue(a, sortKey) : (a as any)[sortKey];
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const bVal = getValue ? getValue(b, sortKey) : (b as any)[sortKey];

      if (aVal == null && bVal == null) return 0;
      if (aVal == null) return 1;
      if (bVal == null) return -1;

      if (typeof aVal === "string" && typeof bVal === "string") {
        return sortDir === "asc"
          ? aVal.localeCompare(bVal, "es")
          : bVal.localeCompare(aVal, "es");
      }
      return sortDir === "asc"
        ? (aVal as number) - (bVal as number)
        : (bVal as number) - (aVal as number);
    });
  }, [data, sortKey, sortDir, getValue]);

  const indicator = (key: string): string => {
    if (key !== sortKey) return "";
    return sortDir === "asc" ? " ▲" : " ▼";
  };

  return { sortedData, sortKey, sortDir, toggleSort, indicator };
}
