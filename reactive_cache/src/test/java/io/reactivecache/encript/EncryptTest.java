/*
 * Copyright 2016 Victor Albertos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.reactivecache.encript;

import io.reactivecache.Jolyglot$;
import io.reactivecache.Mock;
import io.reactivecache.Provider;
import io.reactivecache.ReactiveCache;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.rx_cache.Reply;
import io.rx_cache.Source;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class EncryptTest {
  private final static int SIZE = 1000;
  @ClassRule public static TemporaryFolder temporaryFolder = new TemporaryFolder();
  private Provider<List<Mock>> mocksEncryptedProvider;
  private Provider<List<Mock>> mocksNoEncryptedProvider;

  @Before public void init() {
    ReactiveCache reactiveCache = new ReactiveCache.Builder()
        .encrypt("myStrongKey-1234")
        .using(temporaryFolder.getRoot(), Jolyglot$.newInstance());

    mocksEncryptedProvider = reactiveCache.<List<Mock>>provider()
        .encrypt(true)
        .withKey("mocksEncryptedProvider");
    mocksNoEncryptedProvider = reactiveCache.<List<Mock>>provider()
        .withKey("mocksNoEncryptedProvider");
  }

  @Test public void _00_Save_Record_On_Disk_In_Order_To_Test_Following_Tests() {
    TestObserver<Reply<List<Mock>>> observer = createObservableMocks(SIZE)
        .compose(mocksEncryptedProvider.readWithLoaderAsReply())
        .test();
    observer.awaitTerminalEvent();

    observer = createObservableMocks(SIZE)
        .compose(mocksNoEncryptedProvider.readWithLoaderAsReply())
        .test();

    observer.awaitTerminalEvent();
  }

  @Test public void _01_When_Encrypted_Record_Has_Been_Persisted_And_Memory_Has_Been_Destroyed_Then_Retrieve_From_Disk() {
    TestObserver<Reply<List<Mock>>> observer = Observable.<List<Mock>>just(new ArrayList<>())
        .compose(mocksEncryptedProvider.readWithLoaderAsReply())
        .test();
    observer.awaitTerminalEvent();

    Reply<List<Mock>> reply = observer.values().get(0);
    assertThat(reply.getSource(), is(Source.PERSISTENCE));
    assertThat(reply.isEncrypted(), is(true));
  }

  @Test public void _02_Verify_Encrypted_Does_Not_Propagate_To_Other_Providers() {
    TestObserver<Reply<List<Mock>>> observer = createObservableMocks(SIZE)
        .compose(mocksEncryptedProvider.readWithLoaderAsReply())
        .test();

    observer.awaitTerminalEvent();

    Reply<List<Mock>> reply = observer.values().get(0);
    assertThat(reply.getSource(), is(Source.PERSISTENCE));
    assertThat(reply.isEncrypted(), is(true));

    observer = createObservableMocks(SIZE)
        .compose(mocksNoEncryptedProvider.readWithLoaderAsReply())
        .test();

    observer.awaitTerminalEvent();

    reply = observer.values().get(0);
    assertThat(reply.getSource(), is(Source.PERSISTENCE));
    assertThat(reply.isEncrypted(), is(false));
  }


  private Observable<List<Mock>> createObservableMocks(int size) {
    long currentTime = System.currentTimeMillis();

    List<Mock> mocks = new ArrayList(size);
    for (int i = 0; i < size; i++) {
      mocks.add(new Mock("mock"+currentTime));
    }

    return Observable.just(mocks);
  }
}
