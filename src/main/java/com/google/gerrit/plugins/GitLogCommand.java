// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.change.QueryProcessor;
import com.google.gerrit.server.query.change.QueryProcessor.OutputFormat;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public final class GitLogCommand extends SshCommand {

  @Argument(usage = "name of repository")
  private String name = null;
  
  @Option(name = "--from", usage = "commit to show history from")
  private String from = null;

  @Option(name = "--to", usage = "commit to show history to")
  private String to = null;
  
  @SuppressWarnings("unused")
  @Option(name = "--include-notes", usage = "include git notes in log")
  private Boolean showNotes = false;
  
  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private QueryProcessor.OutputFormat format = OutputFormat.TEXT;
    
  @Inject
  private GitRepositoryManager repoManager;
    
  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    
    if (this.name == null) {
      stdout.print("No repository specified.\n");
      return;
    }
       
    if (this.from == null || this.to == null) {
      stdout.print("Nothing to show log between.\n");
      return;
    }
    
    Project.NameKey repo = Project.NameKey.parse(name);
    Repository git = null;
     
    try {
      git = repoManager.openRepository(repo);

      Git g = Git.open(git.getDirectory());   
      Repository r = g.getRepository();
      
      Map<String, Ref> refs = git.getAllRefs();
      Map<String, ObjectId> list = new HashMap<String, ObjectId>();
      
      LogCommand log = g.log();    

      list.put(this.from, null);
      list.put(this.to, null);
      
      for(String s: list.keySet()) {
        // not really a proper sha1 check here :]
        if (s.length() != 40) {
          if (! refs.containsKey(s)) {
            stdout.print(s + " does not point to a valid git reference.\n");
            return;
          } else {
            list.put(s, refs.get(s).getObjectId());
            }
        } else {
          list.put(s, ObjectId.fromString(s));
        }
          
      }

      log.addRange(list.get(this.from), list.get(this.to));
        
      try {
        for(RevCommit rev: log.call()) {
          
          PersonIdent author = rev.getAuthorIdent();
          Date date = new Date(rev.getCommitTime());      
          
          // serialize and send out on wire.
          if (this.format == OutputFormat.TEXT) {
            String msg = "commit " + rev.name() + "\n";
            msg += "Author: " + author.getName() + " " + author.getEmailAddress() + "\n";
            msg += "Date: " + date.toString() + "\n\n";
            msg += rev.getFullMessage() + "\n";
            stdout.print(msg);
          }
        }
      } finally {
        git.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }    
  }
}
