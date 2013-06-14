// Copyright (C) 2000 Christopher J. Terman - All Rights Reserved.

package gui;

public interface ProgressTracker {
  boolean ProgressStart(Object owner);
  void ProgressReport(Object owner,double progress);
  long ProgressStop(Object owner);
}
