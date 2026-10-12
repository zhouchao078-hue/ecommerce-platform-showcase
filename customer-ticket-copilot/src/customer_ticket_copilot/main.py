import json

from dotenv import load_dotenv

from .crew import create_crew


def main() -> None:
    load_dotenv()
    ticket = "客户反馈收到的产品无法启动，希望尽快解决。"
    knowledge = "产品支持中心：请客户提供订单号、设备型号和错误截图；无法启动问题由技术支持处理。"
    result = create_crew().kickoff(inputs={"ticket": ticket, "knowledge": knowledge})
    print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2, default=str))
    print("\n提示：这是回复草稿，发送前必须由客服确认。")


if __name__ == "__main__":
    main()
