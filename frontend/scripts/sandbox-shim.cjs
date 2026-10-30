/**
 * 沙箱构建垫片（仅开发/构建期使用，不进入产物）
 *
 * 背景：DSH 的文件沙箱禁止以 piped stdio 启动子进程（spawn 会报 EPERM）。
 * Vite 在 Windows 下会执行 `net use` 来探测网络盘的真实路径，因此构建会中断。
 *
 * 处理：只拦截这一条命令并返回空结果，其它子进程调用保持原样。
 * 这不改变任何构建产物的内容，只是跳过一次环境探测。
 */
const childProcess = require('node:child_process')

const originalExec = childProcess.exec

childProcess.exec = function patchedExec(command, options, callback) {
  const normalized = typeof command === 'string' ? command.trim().toLowerCase() : ''
  if (normalized === 'net use') {
    const cb = typeof options === 'function' ? options : callback
    if (typeof cb === 'function') {
      process.nextTick(() => cb(null, '', ''))
    }
    const fakeChild = {
      on() {
        return fakeChild
      },
      once() {
        return fakeChild
      },
      kill() {
        return true
      },
    }
    return fakeChild
  }
  return originalExec.apply(this, arguments)
}
