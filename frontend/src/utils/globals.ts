import type { MessageInstance } from 'antd/es/message/interface';
import type { NotificationInstance } from 'antd/es/notification/interface';
import type { HookAPI } from 'antd/es/modal/useModal';

/** Tham chiếu tới message/notification/modal của antd App (được gán trong AntdGlobals). */
export const ui = {} as { message: MessageInstance; notification: NotificationInstance; modal: HookAPI };
