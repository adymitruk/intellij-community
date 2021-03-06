/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.CacheUtil;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.SubstitutedFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: dmitrylomov
 */
public abstract class PlatformIdTableBuilding {
  static final HashMap<FileType, DataIndexer<TodoIndexEntry, Integer, FileContent>> ourTodoIndexers =
    new HashMap<FileType, DataIndexer<TodoIndexEntry, Integer, FileContent>>();
  private static final TokenSet ABSTRACT_FILE_COMMENT_TOKENS =
    TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);

  private PlatformIdTableBuilding() {}

  @Nullable
  public static DataIndexer<TodoIndexEntry, Integer, FileContent> getTodoIndexer(FileType fileType, final VirtualFile virtualFile) {
    final DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = ourTodoIndexers.get(fileType);

    if (indexer != null) {
      return indexer;
    }

    final DataIndexer<TodoIndexEntry, Integer, FileContent> extIndexer;
    if (fileType instanceof SubstitutedFileType) {
      SubstitutedFileType sft = (SubstitutedFileType)fileType;
      extIndexer =
        new CompositeTodoIndexer(getTodoIndexer(sft.getOriginalFileType(), virtualFile), getTodoIndexer(sft.getFileType(), virtualFile));
    }
    else {
      extIndexer = TodoIndexers.INSTANCE.forFileType(fileType);
    }
    if (extIndexer != null) {
      return extIndexer;
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens() : null;
      if (commentTokens != null) {
        return new TokenSetTodoIndexer(commentTokens, virtualFile);
      }
    }

    if (fileType instanceof CustomSyntaxTableFileType) {
      return new TokenSetTodoIndexer(ABSTRACT_FILE_COMMENT_TOKENS, virtualFile);
    }

    return null;
  }

  public static boolean checkCanUseCachedEditorHighlighter(final CharSequence chars, final EditorHighlighter editorHighlighter) {
    assert editorHighlighter instanceof LexerEditorHighlighter;
    final boolean b = ((LexerEditorHighlighter)editorHighlighter).checkContentIsEqualTo(chars);
    if (!b) {
      final Logger logger = Logger.getInstance(IdTableBuilding.class.getName());
      logger.warn("Unexpected mismatch of editor highlighter content with indexing content");
    }
    return b;
  }

  @Deprecated
  public static void registerTodoIndexer(FileType fileType, DataIndexer<TodoIndexEntry, Integer, FileContent> indexer) {
    ourTodoIndexers.put(fileType, indexer);
  }

  public static boolean isTodoIndexerRegistered(FileType fileType) {
    return ourTodoIndexers.containsKey(fileType) || TodoIndexers.INSTANCE.forFileType(fileType) != null;
  }

  private static class CompositeTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {

    private final DataIndexer<TodoIndexEntry, Integer, FileContent>[] indexers;

    public CompositeTodoIndexer(DataIndexer<TodoIndexEntry, Integer, FileContent>... indexers) {
      this.indexers = indexers;
    }

    @NotNull
    @Override
    public Map<TodoIndexEntry, Integer> map(FileContent inputData) {
      Map<TodoIndexEntry, Integer> result = CollectionFactory.troveMap();
      for (DataIndexer<TodoIndexEntry, Integer, FileContent> indexer : indexers) {
        for (Map.Entry<TodoIndexEntry, Integer> entry : indexer.map(inputData).entrySet()) {
          TodoIndexEntry key = entry.getKey();
          if (result.containsKey(key)) {
            result.put(key, result.get(key) + entry.getValue());
          } else {
            result.put(key, entry.getValue());
          }
        }
      }
      return result;
    }
  }

  private static class TokenSetTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
    @NotNull private final TokenSet myCommentTokens;
    private final VirtualFile myFile;

    public TokenSetTodoIndexer(@NotNull final TokenSet commentTokens, @NotNull final VirtualFile file) {
      myCommentTokens = commentTokens;
      myFile = file;
    }

    @Override
    @NotNull
    public Map<TodoIndexEntry, Integer> map(final FileContent inputData) {
      if (IndexPatternUtil.getIndexPatternCount() > 0) {
        final CharSequence chars = inputData.getContentAsText();
        final OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
        EditorHighlighter highlighter;

        final EditorHighlighter editorHighlighter = inputData.getUserData(FileBasedIndexImpl.EDITOR_HIGHLIGHTER);
        if (editorHighlighter != null && checkCanUseCachedEditorHighlighter(chars, editorHighlighter)) {
          highlighter = editorHighlighter;
        }
        else {
          highlighter = HighlighterFactory.createHighlighter(null, myFile);
          highlighter.setText(chars);
        }

        final int documentLength = chars.length();
        BaseFilterLexer.TodoScanningData[] todoScanningDatas = null;
        final HighlighterIterator iterator = highlighter.createIterator(0);

        while (!iterator.atEnd()) {
          final IElementType token = iterator.getTokenType();

          if (myCommentTokens.contains(token) || CacheUtil.isInComments(token)) {
            int start = iterator.getStart();
            if (start >= documentLength) break;
            int end = iterator.getEnd();

            todoScanningDatas = BaseFilterLexer.advanceTodoItemsCount(
              chars.subSequence(start, Math.min(end, documentLength)),
              occurrenceConsumer,
              todoScanningDatas
            );
            if (end > documentLength) break;
          }
          iterator.advance();
        }
        final Map<TodoIndexEntry, Integer> map = new HashMap<TodoIndexEntry, Integer>();
        for (IndexPattern pattern : IndexPatternUtil.getIndexPatterns()) {
          final int count = occurrenceConsumer.getOccurrenceCount(pattern);
          if (count > 0) {
            map.put(new TodoIndexEntry(pattern.getPatternString(), pattern.isCaseSensitive()), count);
          }
        }
        return map;
      }
      return Collections.emptyMap();
    }
  }

  public static class PlainTextTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
    @Override
    @NotNull
    public Map<TodoIndexEntry, Integer> map(final FileContent inputData) {
      final CharSequence chars = inputData.getContentAsText();


      final IndexPattern[] indexPatterns = IndexPatternUtil.getIndexPatterns();
      if (indexPatterns.length > 0) {
        final OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
        for (IndexPattern indexPattern : indexPatterns) {
          Pattern pattern = indexPattern.getPattern();
          if (pattern != null) {
            Matcher matcher = pattern.matcher(chars);
            while (matcher.find()) {
              if (matcher.start() != matcher.end()) {
                occurrenceConsumer.incTodoOccurrence(indexPattern);
              }
            }
          }
        }
        Map<TodoIndexEntry, Integer> map = new HashMap<TodoIndexEntry, Integer>();
        for (IndexPattern indexPattern : indexPatterns) {
          final int count = occurrenceConsumer.getOccurrenceCount(indexPattern);
          if (count > 0) {
            map.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
          }
        }
        return map;
      }
      return Collections.emptyMap();
    }

  }

  static {
    IdTableBuilding.registerIdIndexer(FileTypes.PLAIN_TEXT, new IdTableBuilding.PlainTextIndexer());
    registerTodoIndexer(FileTypes.PLAIN_TEXT, new PlainTextTodoIndexer());

    IdTableBuilding.registerIdIndexer(StdFileTypes.IDEA_MODULE, null);
    IdTableBuilding.registerIdIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    IdTableBuilding.registerIdIndexer(StdFileTypes.IDEA_PROJECT, null);

    registerTodoIndexer(StdFileTypes.IDEA_MODULE, null);
    registerTodoIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    registerTodoIndexer(StdFileTypes.IDEA_PROJECT, null);
  }
}
