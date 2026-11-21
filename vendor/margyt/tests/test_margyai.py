"""AI mode isolation, real HTTP format, reports and existing bot command regressions.
All traffic goes to an in-process fake API; no Telegram messages or real keys.
"""
import contextlib
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile
import threading
import time
import types
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'server'))
import margyai

OWNER=7826361017
MAIN=-1001234567890
BOT=123456789


def message(text, chat=MAIN, author=22, mid=1, topic=0, **more):
    private=chat>0
    result={'message_id':mid,'date':int(time.time()),'chat':{'id':chat,'type':'private' if private else 'supergroup','title':'Тестовый чат'},'from':{'id':author,'first_name':'Пользователь'},'text':text}
    if topic:result.update(is_topic_message=True,message_thread_id=topic)
    result.update(more)
    return result


class AIFixture(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.root=Path(self.folder.name)
        (self.root/'key').write_text('test-key-not-real')
        self.sent=[];self.judged=[];self.stopped=[];self.fail_owner=False
        self.result={'reply':'Да, я тут.','report':None}
        self.serial=1000
        def transport(method,**fields):
            self.sent.append((method,fields))
            self.serial+=1
            return {'ok':not (self.fail_owner and fields.get('chat_id')==OWNER),'result':{'message_id':self.serial}}
        self.transport=transport
        def judge(messages):
            self.judged.append(messages)
            return self.result
        self.judge=judge
        self.a=self.make()

    def make(self):
        return margyai.Assistant(OWNER,self.transport,lambda:{'id':BOT,'username':'margy_robot'},self.stopped.append,db=self.root/'ai.db',key_file=self.root/'key',knowledge=ROOT/'server/margyai_knowledge.md',store_db=self.root/'no-store.db',judge=self.judge)

    def tearDown(self):
        self.folder.cleanup()

    def enable(self):
        self.a.command(message('/setchat',author=OWNER),'/setchat')
        self.a.command(message('/margyai on',author=OWNER),'/margyai on')
        self.sent.clear()

    def ask(self,text='@margy_robot привет',**args):
        self.assertTrue(self.a.hear(message(text,**args)))
        with patch.object(margyai.time,'monotonic',return_value=time.monotonic()+100):
            job=self.a.take()
        self.assertIsNotNone(job)
        self.a.run_one(job)
        return job

    def count_messages(self,chat=None):
        with self.a.connect() as c:
            return c.execute('SELECT COUNT(*) FROM messages'+(' WHERE chat=?' if chat is not None else ''), (chat,) if chat is not None else ()).fetchone()[0]

class AITest(AIFixture):
    def test_default_off_does_not_store_or_call(self):
        self.assertFalse(self.a.hear(message('@margy_robot привет')))
        self.assertFalse(self.a.hear(message('привет',chat=OWNER,author=OWNER)))
        self.assertEqual(self.count_messages(),0);self.assertEqual(self.judged,[])
        self.assertEqual(os.stat(self.a.db).st_mode & 0o777,0o600)

    def test_only_owner_controls_settings(self):
        for command in ['/setchat','/margyai on','/margyai off','/margyai forget']:
            self.assertTrue(self.a.command(message(command),command))
        self.assertFalse(self.a.state()['active']);self.assertEqual(self.a.state()['main_chat'],0)
        self.assertEqual(self.judged,[])

    def test_anonymous_owner_cannot_control(self):
        self.a.command(message('/setchat',author=OWNER,sender_chat={'id':MAIN}),'/setchat')
        self.assertEqual(self.a.state()['main_chat'],0)

    def test_setchat_needs_group_and_other_bot_suffix_ignored(self):
        self.a.command(message('/setchat',chat=OWNER,author=OWNER),'/setchat')
        self.assertEqual(self.a.state()['main_chat'],0)
        self.assertFalse(self.a.command(message('/setchat@someone_else',author=OWNER),'/setchat@someone_else'))
        self.assertEqual(self.a.state()['main_chat'],0)

    def test_on_needs_selected_chat_and_key(self):
        self.a.command(message('/margyai on',author=OWNER),'/margyai on')
        self.assertFalse(self.a.state()['active'])
        self.a.command(message('/setchat',author=OWNER),'/setchat')
        (self.root/'key').unlink()
        self.a.command(message('/margyai on',author=OWNER),'/margyai on')
        self.assertFalse(self.a.state()['active'])

    def test_selected_chat_and_owner_dm_only(self):
        self.enable()
        for m in [message('привет',chat=MAIN-1),message('привет',chat=33,author=33),message('привет',chat=OWNER,author=33)]:
            self.assertFalse(self.a.hear(m))
        self.assertEqual(self.count_messages(),0);self.assertEqual(self.judged,[])
        self.ask('привет',chat=OWNER,author=OWNER)
        self.assertEqual(self.sent[-1][1]['chat_id'],OWNER)
        self.assertEqual(self.stopped,[MAIN])

    def test_on_from_owner_dm_off_and_no_chatter_restart(self):
        self.a.command(message('/setchat',author=OWNER),'/setchat')
        self.a.command(message('/margyai on',chat=OWNER,author=OWNER),'/margyai on')
        self.assertTrue(self.a.state()['active']);self.assertEqual(self.stopped,[MAIN])
        self.a.command(message('/margyai off',chat=OWNER,author=OWNER),'/margyai off')
        self.assertFalse(self.a.state()['active']);self.assertEqual(self.stopped,[MAIN])

    def test_on_from_foreign_group_denied(self):
        self.a.command(message('/setchat',author=OWNER),'/setchat')
        self.a.command(message('/margyai on',chat=MAIN-1,author=OWNER),'/margyai on')
        self.assertFalse(self.a.state()['active'])

    def test_mention_replies_plain_no_emoji(self):
        self.enable();self.result={'reply':'Привет ❤️ 😎 <b>человек</b>','report':None}
        job=self.ask('@MARGY_ROBOT привет')
        self.assertTrue(job['direct'])
        fields=self.sent[-1][1]
        self.assertNotIn('parse_mode',fields);self.assertNotIn('😎',fields['text']);self.assertIn('<b>человек</b>',fields['text'])
        self.assertEqual(fields['reply_parameters']['message_id'],1)

    def test_reply_to_bot_is_direct_and_topic_is_preserved(self):
        self.enable()
        job=self.ask('продолжи',topic=77,reply_to_message={'from':{'id':BOT},'text':'предыдущий ответ'})
        self.assertTrue(job['direct']);self.assertEqual(self.sent[-1][1]['message_thread_id'],77)

    def test_existing_plain_text_history_becomes_valid_json_examples(self):
        self.enable()
        self.a.remember(message('Старый ответ с "кавычками"\nи переносом',mid=2),
                        'assistant','Старый ответ с "кавычками"\nи переносом')
        self.ask('@margy_robot продолжи',mid=3)
        history=[m for m in self.judged[-1] if m['role']=='assistant']
        self.assertEqual(len(history),1)
        self.assertEqual(json.loads(history[0]['content']),
                         {'reply':'Старый ответ с "кавычками"\nи переносом','report':None})
        self.assertTrue(self.judged[-1][-1]['content'].endswith(margyai.OUTPUT_FORMAT))
        with self.a.connect() as c:
            saved=c.execute('SELECT body FROM messages WHERE message_id=2').fetchone()[0]
        self.assertIsInstance(json.loads(saved),str)

    def test_lookalike_mention_and_reply_to_human_are_passive(self):
        self.enable();self.result={'reply':None,'report':None}
        job=self.ask('@margy_robot_extra привет',reply_to_message={'from':{'id':33},'text':'чужой разговор'})
        self.assertFalse(job['direct']);self.assertEqual(self.sent,[])

    def test_direct_response_never_learns_outside_scope_and_duplicates(self):
        self.enable();m=message('@margy_robot привет')
        self.assertTrue(self.a.hear(m));self.assertTrue(self.a.hear(m))
        self.assertEqual(len(self.a.direct),1);self.assertEqual(self.count_messages(),1)
        m['from']['is_bot']=True;m['message_id']=2
        self.assertFalse(self.a.hear(m))

    def test_passive_batches_and_cooldown(self):
        self.enable();self.result={'reply':None,'report':None}
        for i in range(1,8):self.a.hear(message('обычный разговор',mid=i))
        self.assertEqual(len(self.a.passive),1);self.assertEqual(self.count_messages(),7)
        with patch.object(margyai.time,'monotonic',return_value=time.monotonic()+100):job=self.a.take()
        self.a.run_one(job)
        self.assertEqual(len(self.judged),1);self.assertEqual(self.sent,[])
        self.a.hear(message('ещё разговор',mid=8))
        self.assertIsNone(self.a.take())

    def test_switch_and_off_cancel_inflight_response_and_report(self):
        self.enable();self.a.hear(message('@margy_robot привет'));job=self.a.take()
        def switched(_):
            self.a.configure(main_chat=MAIN-1)
            return {'reply':'устаревшее','report':{'kind':'idea','project':'Margy Chat','summary':'идея','details':'детали'}}
        self.a.judge=switched
        self.a.run_one(job);self.assertEqual(self.sent,[])
        self.assertFalse(self.a.hear(message('старый чат',mid=2)))

    def test_old_queued_jobs_after_off_are_ignored(self):
        self.enable();self.a.hear(message('@margy_robot привет'));job=self.a.take()
        self.a.configure(active=0);self.a.run_one(job)
        self.assertEqual(self.judged,[]);self.assertEqual(self.sent,[])

    def test_setting_persist_and_no_owner_dm_leak_to_group(self):
        self.enable();self.ask('Секретный разговор владельца',chat=OWNER,author=OWNER)
        self.a=self.make();self.assertTrue(self.a.state()['active'])
        self.ask('@margy_robot групповой разговор')
        self.assertNotIn('Секретный разговор владельца',json.dumps(self.judged[-1],ensure_ascii=False))

    def test_topics_and_history_are_isolated(self):
        self.enable();self.ask('@margy_robot топик один',topic=77)
        self.ask('@margy_robot топик два',mid=2,topic=88)
        self.assertNotIn('топик один',json.dumps(self.judged[-1],ensure_ascii=False))

    def test_forget_only_clears_current_chat_and_cancel_jobs(self):
        self.enable();self.a.hear(message('основная группа'));self.a.hear(message('личка',chat=OWNER,author=OWNER))
        self.a.command(message('/margyai forget',author=OWNER),'/margyai forget')
        self.assertEqual(self.count_messages(MAIN),0);self.assertEqual(self.count_messages(OWNER),1)
        self.assertFalse(self.a.direct);self.assertFalse(self.a.passive)

    def test_secrets_not_in_context_and_attachments_not_faked(self):
        self.enable();key='sk-not-a-real-secret-token-with-more-than-sixteen-characters'
        self.ask('@margy_robot '+key,photo=[{'file_id':'not-a-real-file'}])
        prompt=json.dumps(self.judged[-1],ensure_ascii=False)
        self.assertNotIn(key,prompt);self.assertIn('секрет скрыт',prompt);self.assertIn('файл не переданы модели',prompt)

    def test_history_limit_expiry_and_old_updates(self):
        self.enable()
        for i in range(1,80):self.a.hear(message('привет',mid=i))
        self.assertEqual(self.count_messages(),48)
        with self.a.connect() as c:c.execute('UPDATE messages SET at=0')
        self.a.hear(message('текущее',mid=80))
        self.assertEqual(self.count_messages(),1)
        self.a.hear(message('@margy_robot старое',mid=81,date=int(time.time())-1000))
        self.assertEqual(self.count_messages(),1)

    def test_expired_context_is_cleaned_even_when_disabled(self):
        self.enable();self.a.hear(message('старый текст'))
        self.a.configure(active=0)
        with self.a.connect() as c:c.execute('UPDATE messages SET at=0')
        self.a.prune()
        self.assertEqual(self.count_messages(),0)

    def test_explicit_queue_bounded_and_requests_limited(self):
        self.enable()
        for i in range(1,100):self.a.hear(message('@margy_robot вопрос',mid=i))
        self.assertEqual(len(self.a.direct),24)
        self.sent.clear()
        for _ in range(10):self.a.run_one(self.a.take())
        self.assertEqual(len(self.judged),6)

    def report_result(self):
        return {'reply':'Идея понятная.','report':{'kind':'idea','project':'Margy Chat','summary':'Добавить счётчик котов','details':'Показывать котов рядом с профилем','chat_id':999}}

    def test_report_only_to_owner_with_source_no_topic(self):
        self.enable();self.result=self.report_result()
        self.ask('@margy_robot передай идею: счётчик котов',topic=77)
        owner=[f for _,f in self.sent if f['chat_id']==OWNER]
        self.assertEqual(len(owner),1);self.assertNotIn('message_thread_id',owner[0]);self.assertNotIn('reply_parameters',owner[0])
        self.assertIn('https://t.me/c/1234567890/1',owner[0]['text']);self.assertIn('Пересказ ИИ',owner[0]['text'])
        self.assertIn('Передал Нарезанному',self.sent[-1][1]['text'])
        self.assertFalse(any(f['chat_id']==999 for _,f in self.sent))

    def test_report_uses_original_author_and_validates_source(self):
        self.enable()
        original=message('Добавить счётчик котов',author=22,mid=1)
        original['from']['first_name']='Автор идеи'
        self.a.hear(original)
        self.result=self.report_result();self.result['report']['source_message_id']=1
        self.ask('@margy_robot передай идею выше',author=33,mid=2)
        owner=[f for _,f in self.sent if f['chat_id']==OWNER]
        self.assertIn('Автор идеи',owner[0]['text']);self.assertIn('https://t.me/c/1234567890/1',owner[0]['text'])
        with self.a.connect() as c:self.assertEqual(c.execute('SELECT author FROM reports').fetchone()[0],22)
        self.result['report']['source_message_id']=999999
        self.ask(mid=3)
        self.assertEqual(sum(f['chat_id']==OWNER for _,f in self.sent),1)

    def test_report_failure_never_claims_delivery(self):
        self.enable();self.result=self.report_result();self.fail_owner=True
        self.ask()
        self.assertIn('доставить не получилось',self.sent[-1][1]['text']);self.assertNotIn('Передал Нарезанному',self.sent[-1][1]['text'])
        with self.a.connect() as c:self.assertEqual(c.execute('SELECT delivered FROM reports').fetchone()[0],0)

    def test_duplicate_report_and_quota(self):
        self.enable();self.result=self.report_result()
        self.ask(mid=1);self.ask(mid=2)
        self.assertEqual(sum(f['chat_id']==OWNER for _,f in self.sent),1)
        for i in range(3,7):
            self.result['report']['summary']='Идея номер '+str(i)
            self.ask(mid=i)
        self.assertEqual(sum(f['chat_id']==OWNER for _,f in self.sent),3)

    def test_no_foreign_project_or_arbitrary_action(self):
        self.enable();self.result={'reply':'Нет.','report':{'kind':'exec','project':'Margy Chat','summary':'cmd'}}
        self.ask()
        self.result={'reply':'Нет.','report':{'kind':'bug','project':'Другой сервис','summary':'текст'}}
        self.ask(mid=2)
        self.assertFalse(any(f['chat_id']==OWNER for _,f in self.sent))

    def test_provider_failure_backoff_and_no_passive_error_spam(self):
        self.enable()
        def fail(_):raise margyai.Trouble(429,100)
        self.a.judge=fail;self.ask();self.assertIn('сейчас не отвечает',self.sent[-1][1]['text'])
        self.assertGreater(self.a.backoff,time.monotonic())
        count=len(self.sent);self.ask(mid=2)
        self.assertEqual(len(self.sent),count)

    def test_catalog_only_released_official_and_no_private_accounts(self):
        c=sqlite3.connect(self.a.store_db)
        c.executescript("CREATE TABLE apps(id INTEGER,name TEXT,slug TEXT,summary TEXT,status TEXT,official INTEGER);CREATE TABLE releases(id INTEGER,app_id INTEGER,version TEXT,status TEXT,created INTEGER);INSERT INTO apps VALUES(10,'MargyT','margyt','Мод','approved',1),(11,'Не выпущено','hidden','private','pending',1),(12,'Чужое','other','not official','approved',0);INSERT INTO releases VALUES(1,10,'1.0','approved',0),(2,10,'секретная бета','pending',1);")
        c.close();text=self.a.catalog()
        self.assertIn('https://margy.narez.xyz/app/10',text);self.assertIn('1.0',text)
        for secret in ['секретная бета','Не выпущено','Чужое']:self.assertNotIn(secret,text)


class RetryAITest(AIFixture):
    def test_transient_failures_retry_same_image_and_deliver_report_once(self):
        self.enable()
        report = {'kind':'idea','project':'Margy Chat','summary':'Показывать режим записи',
                  'details':'Показывать индикатор, когда участник пишет ответ.','source_message_id':1}
        result = {'reply':'Хорошая идея.','report':report}
        with patch.object(self.a, 'judge', side_effect=[margyai.Trouble(503),
                          margyai.Trouble(reason='network_error'), result]) as judge, \
             patch.object(self.a, 'wait_retry', return_value=True) as wait, \
             patch.object(self.a, 'image_loader', return_value='data:image/jpeg;base64,YWJj') as image:
            self.ask('@margy_robot идея для Margy Chat: показывать режим записи',
                     photo=[{'file_id':'photo1','width':100,'height':100}])
        self.assertEqual(judge.call_count,3);self.assertEqual(image.call_count,1)
        self.assertIs(judge.call_args_list[0].args[0],judge.call_args_list[2].args[0])
        self.assertEqual([c.args[1] for c in wait.call_args_list],[2,4])
        self.assertEqual(sum(f['chat_id']==OWNER for _,f in self.sent),1)
        self.assertEqual(sum(f['chat_id']==MAIN for _,f in self.sent),1)
        self.assertEqual(self.count_messages(),2);self.assertEqual(self.a.backoff,0)
        self.assertEqual(len(self.a.rates[('chat',MAIN)]),3)

    def test_exhausted_retries_send_only_one_error(self):
        self.enable()
        with patch.object(self.a,'judge',side_effect=margyai.Trouble(502)) as judge, \
             patch.object(self.a,'wait_retry',return_value=True):
            self.ask()
        self.assertEqual(judge.call_count,3);self.assertEqual(len(self.sent),1)
        self.assertIn('сейчас не отвечает',self.sent[0][1]['text'])
        self.assertGreater(self.a.backoff,time.monotonic())
        self.assertEqual(self.count_messages(),1)

    def test_auth_failure_is_not_retried(self):
        self.enable()
        with patch.object(self.a,'judge',side_effect=margyai.Trouble(401)) as judge, \
             patch.object(self.a,'wait_retry') as wait:
            self.ask()
        self.assertEqual(judge.call_count,1);wait.assert_not_called()

    def test_provider_retry_after_is_respected(self):
        self.enable()
        with patch.object(self.a,'judge',side_effect=[margyai.Trouble(503,retry_after=12),self.result]) as judge, \
             patch.object(self.a,'wait_retry',return_value=True) as wait:
            self.ask()
        self.assertEqual(judge.call_count,2);self.assertEqual(wait.call_args.args[1],12)
        self.assertEqual(len(self.sent),1)

    def test_long_retry_after_does_not_retry_early(self):
        self.enable()
        with patch.object(self.a,'judge',side_effect=margyai.Trouble(429,90,retry_after=90)) as judge, \
             patch.object(self.a,'wait_retry') as wait:
            self.ask()
        self.assertEqual(judge.call_count,1);wait.assert_not_called()
        self.assertGreater(self.a.backoff,time.monotonic()+85)

    def test_retry_cannot_bypass_request_limit(self):
        self.enable()
        self.a.rates[('user',MAIN,22)]=[time.monotonic()]*5
        with patch.object(self.a,'judge',side_effect=margyai.Trouble(503)) as judge, \
             patch.object(self.a,'wait_retry',return_value=True):
            self.ask()
        self.assertEqual(judge.call_count,1)
        self.assertEqual(len(self.a.rates[('user',MAIN,22)]),6)

    def test_passive_failure_does_not_retry_or_send_error(self):
        self.enable()
        with patch.object(self.a,'judge',side_effect=margyai.Trouble(503)) as judge, \
             patch.object(self.a,'wait_retry') as wait:
            self.ask('обсуждение между участниками')
        self.assertEqual(judge.call_count,1);wait.assert_not_called()
        self.assertEqual(self.sent,[])

    def test_settings_change_wakes_retry_and_cancels_delivery(self):
        self.enable();self.a.hear(message('@margy_robot привет'))
        job=self.a.take();waiting=threading.Event()
        original=self.a.wait_retry
        def wait(job,delay):
            waiting.set()
            return original(job,delay)
        with patch.object(self.a,'judge',side_effect=margyai.Trouble(503)) as judge, \
             patch.object(self.a,'wait_retry',side_effect=wait):
            thread=threading.Thread(target=self.a.run_one,args=(job,));thread.start()
            self.assertTrue(waiting.wait(1))
            self.a.configure(active=0);thread.join(1)
            self.assertFalse(thread.is_alive());self.assertEqual(judge.call_count,1)
        self.assertEqual(self.sent,[]);self.assertEqual(self.a.backoff,0)


class VisionAITest(AIFixture):
    def setUp(self):
        super().setUp()
        self.loaded=[]
        self.url='data:image/jpeg;base64,dGVzdA=='
        self.a.image_loader=lambda ref:(self.loaded.append(ref) or self.url)

    def photo(self,ident='photo-id'):
        return [{'file_id':ident,'file_size':200,'width':640,'height':480}]

    def parts(self):
        return self.judged[-1][-1]['content']

    def test_owner_picture_without_caption(self):
        self.enable()
        self.ask('',chat=OWNER,author=OWNER,photo=self.photo())
        self.assertTrue(any(p['type']=='image_url' and p['image_url']['url']==self.url for p in self.parts()))
        prompt=json.dumps(self.judged[-1])
        self.assertNotIn('photo-id',prompt);self.assertNotIn('file_id',prompt)
        self.assertEqual(len(self.loaded),1)
        self.assertEqual(self.parts()[-1],{'type':'text','text':margyai.OUTPUT_FORMAT})

    def test_photo_reply_to_bot_without_caption_is_direct(self):
        self.enable()
        job=self.ask('',photo=self.photo(),reply_to_message={'from':{'id':BOT},'text':'покажи ошибку'})
        self.assertTrue(job['direct']);self.assertEqual(len(self.loaded),1)

    def test_image_document_and_replied_to_image(self):
        self.enable()
        quoted=message('',mid=9,photo=self.photo('quoted-image'))
        self.ask('@margy_robot что тут?',mid=10,reply_to_message=quoted)
        self.assertEqual(self.loaded[0]['file_id'],'quoted-image')
        self.ask('@margy_robot а тут?',mid=11,document={'file_id':'document-image','mime_type':'image/png','file_size':400})
        self.assertEqual(self.loaded[-1]['file_id'],'document-image')
        self.assertEqual(margyai.image_ref(message('',document={'file_id':'pdf','mime_type':'application/pdf','file_name':'a.pdf'})),None)

    def test_previous_image_followup_cached_and_scopes_isolated(self):
        self.enable()
        self.ask('',chat=OWNER,author=OWNER,photo=self.photo())
        self.ask('а что написано слева?',chat=OWNER,author=OWNER,mid=1002)
        self.assertEqual(len(self.loaded),1)
        self.assertTrue(any(p['type']=='image_url' for p in self.parts()))
        self.ask('@margy_robot групповой вопрос')
        self.assertIsInstance(self.parts(),str)
        self.assertNotIn(self.url,json.dumps(self.judged[-1]))

    def test_cached_images_expire_and_forget_clears_pixels(self):
        self.enable();self.ask('',chat=OWNER,author=OWNER,photo=self.photo())
        self.assertTrue(self.a._images)
        with patch.object(margyai.time,'monotonic',return_value=time.monotonic()+601):self.a.prune()
        self.assertEqual(self.a._images,{})
        self.ask('',chat=OWNER,author=OWNER,mid=1002,photo=self.photo('another'))
        self.a.command(message('/margyai forget',chat=OWNER,author=OWNER),'/margyai forget')
        self.assertEqual(self.a._images,{})

    def test_foreign_photos_not_downloaded_or_stored(self):
        self.enable()
        for m in [message('',chat=MAIN-1,photo=self.photo()),message('',chat=99,author=99,photo=self.photo())]:
            self.assertFalse(self.a.hear(m))
        self.assertEqual(self.loaded,[]);self.assertEqual(self.count_messages(),0)

    def test_images_from_foreign_quote_are_ignored(self):
        self.enable()
        self.ask('@margy_robot глянь',reply_to_message=message('',chat=MAIN-1,photo=self.photo()))
        self.assertEqual(self.loaded,[])

    def test_loader_failure_not_sent_to_model(self):
        self.enable()
        def failed(ref):raise margyai.ImageTrouble('Картинка слишком большая.')
        self.a.image_loader=failed
        self.ask('@margy_robot фото',photo=self.photo())
        self.assertEqual(self.judged,[]);self.assertIn('слишком большая',self.sent[-1][1]['text'])
        self.assertEqual(self.a.backoff,0)

    def test_off_during_download_prevents_model_request(self):
        self.enable()
        def switched(ref):self.a.configure(active=0);return self.url
        self.a.image_loader=switched
        self.ask('@margy_robot фото',photo=self.photo())
        self.assertEqual(self.judged,[]);self.assertEqual(self.sent,[]);self.assertEqual(self.a._images,{})

    def test_url_with_telegram_credential_is_never_accepted_as_image(self):
        self.enable();self.a.image_loader=lambda ref:'https://api.telegram.org/file/botFAKE_TOKEN/photos/a.jpg'
        self.ask('@margy_robot фото',photo=self.photo())
        self.assertEqual(self.judged,[])
        self.assertNotIn('FAKE_TOKEN',json.dumps(self.sent))

    def test_selects_good_photo_and_limits_images_per_request(self):
        photos=[{'file_id':'small','width':400,'height':300,'file_size':1000},
                {'file_id':'large','width':1000,'height':1000,'file_size':margyai.IMAGE_MOST+1}]
        self.assertEqual(margyai.image_ref(message('',photo=photos))['file_id'],'small')
        self.enable()
        for i in range(1,8):self.a.hear(message('',mid=i,photo=self.photo('file%d'%i)))
        self.ask('@margy_robot сравни последние',mid=8)
        self.assertEqual(len(self.loaded),3)
        self.assertEqual(sum(p['type']=='image_url' for p in self.parts()),3)


class TelegramImageTest(unittest.TestCase):
    def png(self):
        from PIL import Image
        import io
        output=io.BytesIO();Image.new('RGBA',(1800,900),(255,0,0,128)).save(output,format='PNG')
        return output.getvalue()

    def test_download_uses_telegram_and_returns_only_pixels(self):
        import io,base64
        from PIL import Image
        raw=self.png();opened=[];calls=[]
        def get_file(method,**fields):
            calls.append((method,fields));return {'ok':True,'result':{'file_path':'photos/file_1.png','file_size':len(raw)}}
        class Opener:
            def open(self,request,**kwargs):opened.append(request.full_url);return io.BytesIO(raw)
        with patch.object(margyai.urllib.request,'build_opener',return_value=Opener()):
            data=margyai.telegram_image({'file_id':'ABC','file_size':len(raw)},get_file,lambda:'TEST_TOKEN')
        self.assertEqual(calls,[('getFile',{'file_id':'ABC'})])
        self.assertEqual(opened,['https://api.telegram.org/file/botTEST_TOKEN/photos/file_1.png'])
        self.assertTrue(data.startswith('data:image/jpeg;base64,'));self.assertNotIn('TEST_TOKEN',data)
        with Image.open(io.BytesIO(base64.b64decode(data.split(',',1)[1]))) as image:
            self.assertEqual(image.size,(1600,800));self.assertEqual(image.mode,'RGB')

    def test_invalid_telegram_path_does_not_read_token_or_fetch(self):
        for path in ['https://evil.example/photo.jpg','../a.jpg','/a.jpg','photos/a.jpg?token=x','photos//a.jpg','photos/a#x.jpg']:
            read=[]
            with patch.object(margyai.urllib.request,'build_opener') as opener:
                with self.assertRaises(margyai.ImageTrouble):
                    margyai.telegram_image({'file_id':'ABC'},lambda *a,**k:{'ok':True,'result':{'file_path':path}},lambda:read.append(True))
                opener.assert_not_called();self.assertEqual(read,[])

    def test_large_size_or_invalid_bytes_are_safe_errors(self):
        import io
        with self.assertRaises(margyai.ImageTrouble):
            margyai.telegram_image({'file_id':'ABC','file_size':margyai.IMAGE_MOST+1},lambda *a:None,lambda:None)
        class Opener:
            def open(self,*a,**k):return io.BytesIO(b'not an image TEST_TOKEN')
        with patch.object(margyai.urllib.request,'build_opener',return_value=Opener()):
            with self.assertRaises(margyai.ImageTrouble) as raised:
                margyai.telegram_image({'file_id':'ABC'},lambda *a,**k:{'ok':True,'result':{'file_path':'photos/a.jpg'}},lambda:'TEST_TOKEN')
        self.assertNotIn('TEST_TOKEN',str(raised.exception));self.assertNotIn('https://',str(raised.exception))


class ProviderTest(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.key=Path(self.folder.name)/'key';self.key.write_text('fake-secret-key')
        self.requests=[];self.mode='ok'
        case=self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def do_POST(self):
                body=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                case.requests.append((self.path,dict(self.headers),body))
                if case.mode=='redirect':
                    self.send_response(307);self.send_header('Location','/capture');self.end_headers();return
                if case.mode=='limited':
                    self.send_response(429);self.send_header('Retry-After','90');self.end_headers();return
                if case.mode=='busy_once' and len(case.requests)==1:
                    self.send_response(503);self.send_header('Retry-After','0');self.end_headers();return
                if case.mode=='dated_limit':
                    self.send_response(429)
                    until=margyai.datetime.datetime.now(margyai.datetime.timezone.utc)+margyai.datetime.timedelta(seconds=75)
                    self.send_header('Retry-After',margyai.email.utils.format_datetime(until,usegmt=True))
                    self.end_headers();return
                content={'reply':'Привет без роботов.','report':None}
                finish='stop'
                if case.mode=='invalid':content='NOT JSON'
                if case.mode=='blank':content='  \n\t  '
                if case.mode=='truncated':finish='length'
                raw=json.dumps({'choices':[{'message':{'content':content if isinstance(content,str) else json.dumps(content)},'finish_reason':finish}]}).encode()
                self.send_response(200);self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
        self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.url='http://127.0.0.1:%d/v1/chat/completions'%self.server.server_port

    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.folder.cleanup()

    def complete(self):return margyai.completion([{'role':'user','content':'привет'}],self.key,self.url)

    def test_real_http_contract(self):
        self.assertTrue(self.complete()['reply'])
        path,headers,body=self.requests[0]
        self.assertEqual(path,'/v1/chat/completions');self.assertEqual(body['model'],margyai.MODEL);self.assertFalse(body['stream'])
        self.assertEqual(headers['Authorization'],'Bearer fake-secret-key');self.assertEqual(headers['User-Agent'],'MargyHelper/1.0')
        self.assertNotIn('fake-secret-key',json.dumps(body))
        self.assertEqual(body['max_tokens'],4096)
        self.assertEqual(body['response_format'],{'type':'json_object'})

    def test_a_picture_is_described_by_the_model_that_sees(self):
        picture=[{'type':'text','text':'что тут'},{'type':'image_url','image_url':{'url':'data:image/png;base64,AAAA'}}]
        margyai.completion([{'role':'system','content':'x'},{'role':'user','content':picture}],self.key,self.url)
        margyai.completion([{'role':'system','content':'x'},{'role':'user','content':picture}],self.key,self.url)
        models=[r[2]['model'] for r in self.requests]
        # described once, remembered after; the answers both come from the main model, with no picture in them
        self.assertEqual(models,[margyai.VISION_MODEL,margyai.MODEL,margyai.MODEL])
        self.assertNotIn('image_url',json.dumps(self.requests[1][2]));self.assertIn('Описание изображения',json.dumps(self.requests[1][2],ensure_ascii=False))
        self.assertIn('image_url',json.dumps(self.requests[0][2]))

    def test_redirect_does_not_forward_credentials(self):
        self.mode='redirect'
        with self.assertRaises(margyai.Trouble) as raised:self.complete()
        self.assertEqual(raised.exception.status,307);self.assertEqual(len(self.requests),1)
        self.assertFalse(raised.exception.retryable)

    def test_429_retry_after(self):
        self.mode='limited'
        with self.assertRaises(margyai.Trouble) as raised:self.complete()
        self.assertEqual(raised.exception.retry,90)
        self.assertEqual(raised.exception.retry_after,90)

    def test_http_date_retry_after(self):
        self.mode='dated_limit'
        with self.assertRaises(margyai.Trouble) as raised:self.complete()
        self.assertGreater(raised.exception.retry_after,70)
        self.assertLessEqual(raised.exception.retry_after,75)

    def test_invalid_or_truncated_output_is_not_posted(self):
        for mode in ['invalid','truncated','blank']:
            self.mode=mode
            with self.assertRaises(margyai.Trouble) as raised:self.complete()
            self.assertEqual(raised.exception.reason,{'invalid':'invalid_json','truncated':'truncated_response','blank':'empty_response'}[mode])
            self.assertTrue(raised.exception.retryable)

    def test_http_failure_then_recovery_delivers_once(self):
        self.mode='busy_once'
        a=margyai.Assistant(OWNER,lambda *args,**kw:{'ok':True,'result':{'message_id':100}},
                           lambda:{'id':BOT,'username':'margy_robot'},lambda chat:None,
                           db=Path(self.folder.name)/'ai.db',key_file=self.key,
                           store_db=Path(self.folder.name)/'absent',judge=lambda messages:margyai.completion(messages,self.key,self.url))
        a.configure(active=1,main_chat=MAIN);a.hear(message('@margy_robot привет'))
        with patch.object(a,'wait_retry',return_value=True), patch.object(a,'text',return_value={'ok':True,'result':{'message_id':100}}) as send:
            a.run_one(a.take())
        self.assertEqual(len(self.requests),2);send.assert_called_once()
        self.assertEqual(self.requests[0][2],self.requests[1][2])


class BotIntegrationTest(AIFixture):

    def setUp(self):
        super().setUp()
        fake={name:types.ModuleType(name) for name in ['badges','patcher','mc','tiktok']}
        with patch.dict(sys.modules,fake):
            spec=importlib.util.spec_from_file_location('margy_test_bot',ROOT/'server/bot.py')
            self.bot=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.bot)
        self.bot.AI=self.a;self.bot.call=self.transport;self.bot._me.update(id=BOT,username='margy_robot',can_read_all_group_messages=True)

    def test_legacy_auth_start_does_not_go_to_ai(self):
        self.enable();self.bot.patcher.claim=lambda *args:True
        self.bot.handle({'message':message('/start p_test',chat=OWNER,author=OWNER)})
        self.assertIn('Возвращайся',self.sent[-1][1]['text']);self.assertEqual(self.count_messages(),0)

    def test_legacy_minecraft_keeps_its_route(self):
        self.enable();self.bot.mc.bind=lambda *args:'Ник привязан'
        self.bot.handle({'message':message('/mc testplayer',chat=OWNER,author=OWNER)})
        self.assertEqual(self.sent[-1][1]['text'],'Ник привязан');self.assertEqual(self.count_messages(),0)

    def test_chatter_cannot_turn_on_over_ai(self):
        self.enable()
        with patch.object(self.bot.chatter,'state',return_value=(False,8,0)),patch.object(self.bot.chatter,'switch') as switch:
            self.bot.handle({'message':message('/chatter on',author=OWNER)})
        switch.assert_not_called();self.assertIn('/margyai off',self.sent[-1][1]['text'])

    def test_regular_group_message_only_goes_to_ai(self):
        self.enable()
        with patch.object(self.bot.chatter,'hear') as hear:
            self.bot.handle({'message':message('разговор')})
        hear.assert_not_called();self.assertEqual(self.count_messages(),1)

    def test_profile_bug_question_is_not_a_tiktok_lookup(self):
        self.enable();seen=[]
        self.bot.account=lambda *args:seen.append(True)
        self.bot.handle({'message':message('профиль в Margy Chat не открывается')})
        self.assertEqual(seen,[]);self.assertEqual(self.count_messages(),1)

    def test_photo_only_in_owner_dm_reaches_ai_not_icon_offer(self):
        self.enable();seen=[];self.bot.offered=lambda *args:seen.append(True)
        self.bot.handle({'message':message('',chat=OWNER,author=OWNER,photo=[{'file_id':'photo','width':800,'height':600}])})
        self.assertEqual(seen,[]);self.assertEqual(len(self.a.direct),1)

    def test_explicit_icon_submission_keeps_old_route(self):
        self.enable();seen=[]
        self.bot.offered=lambda *args:seen.append(True)
        self.bot.handle({'message':message('иконка 123456789',photo=[{}])})
        self.assertEqual(seen,[True]);self.assertEqual(self.count_messages(),0)


if __name__=='__main__':unittest.main()
